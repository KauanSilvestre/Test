package com.fadcam.kcam.vision;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.fadcam.FLog;
import com.fadcam.R;
import com.fadcam.kcam.audio.AudioRecordService;
import com.fadcam.kcam.quick.QuickMode;
import com.fadcam.kcam.quick.QuickRecordActivity;
import com.fadcam.kcam.quick.Recorders;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Audio Vision: listens to the microphone and starts recording when sound passes a
 * threshold. Audio clips are recorded by this service itself (stop after a silence
 * timeout); camera clips are started through QuickRecordActivity (needs "Display over other
 * apps") and stopped after a fixed length, then listening resumes.
 */
public class AudioVisionService extends Service {

    public static final String ACTION_STOP = "com.fadcam.kcam.vision.STOP";
    private static final String TAG = "KCamAudioVision";
    private static final String CHANNEL = "kcam_audio_vision";
    private static final int NOTIF_ID = 77_800;
    private static final int RATE = 22050;
    /** Sound must stay above the threshold this long to trigger (ignores clicks). */
    private static final long TRIGGER_MS = 300;
    private static final long MAX_CLIP_MS = 30 * 60_000L;

    private static volatile boolean running;
    private static volatile boolean clipActive;
    private static volatile int lastDb;
    private static volatile int clipCount;

    private volatile boolean loop;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRunning() { return running; }
    public static boolean isClipActive() { return clipActive; }
    public static int lastLevelDb() { return lastDb; }
    public static int clipsRecorded() { return clipCount; }

    public static void start(Context c) {
        ContextCompat.startForegroundService(c, new Intent(c, AudioVisionService.class));
    }

    public static void stop(Context c) {
        c.startService(new Intent(c, AudioVisionService.class).setAction(ACTION_STOP));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(getString(R.string.kcam_vision_listening)),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : 0);
        } catch (Exception e) {
            FLog.e(TAG, "startForeground failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KCam:AudioVision");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(24 * 60 * 60 * 1000L);
        }
        running = true;
        loop = true;
        worker = new Thread(this::run, "KCamAudioVision");
        worker.start();
        return START_STICKY;
    }

    private void shutdown() {
        loop = false;
        running = false;
        if (worker != null) worker.interrupt();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ── main loop ───────────────────────────────────────────────────

    private void run() {
        while (loop) {
            // Do not fight another recorder for the microphone.
            if (Recorders.isCameraRecording(this) || AudioRecordService.isRunning() || Recorders.isScreenRecording(this)) {
                sleep(2000);
                continue;
            }
            if (!listenUntilTriggered()) continue;
            clipCount++;
            int action = AudioVisionPrefs.action(this);
            if (action == AudioVisionPrefs.ACTION_AUDIO) {
                recordAudioClip();
            } else {
                recordCameraClip(action == AudioVisionPrefs.ACTION_FRONT ? QuickMode.FRONT : QuickMode.BACK);
            }
        }
    }

    /** @return true when sound passed the threshold for {@link #TRIGGER_MS}. */
    @SuppressLint("MissingPermission")
    private boolean listenUntilTriggered() {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            sleep(5000);
            return false;
        }
        AudioRecord rec = null;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE / 10 * 2));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                sleep(3000);
                return false;
            }
            rec.startRecording();
            short[] buf = new short[RATE / 20]; // 50 ms
            long aboveSince = 0;
            long lastBusyCheck = SystemClock.elapsedRealtime();
            while (loop) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) continue;
                double acc = 0;
                for (int i = 0; i < n; i++) acc += (double) buf[i] * buf[i];
                int db = (int) Math.max(0, 20 * Math.log10(Math.max(Math.sqrt(acc / n), 1.0) / 32768.0) + 90);
                lastDb = db;
                long now = SystemClock.elapsedRealtime();
                if (db >= AudioVisionPrefs.thresholdDb(this)) {
                    if (aboveSince == 0) aboveSince = now;
                    if (now - aboveSince >= TRIGGER_MS) return true;
                } else {
                    aboveSince = 0;
                }
                if (now - lastBusyCheck > 2000) {
                    lastBusyCheck = now;
                    if (Recorders.isCameraRecording(this) || AudioRecordService.isRunning()) return false;
                }
            }
            return false;
        } catch (Exception e) {
            FLog.w(TAG, "Listening failed", e);
            sleep(3000);
            return false;
        } finally {
            if (rec != null) {
                try {
                    rec.stop();
                } catch (Exception ignored) {
                }
                rec.release();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void recordAudioClip() {
        File dir = AudioRecordService.audioDir(this);
        File out = new File(dir, "AudioVision_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".m4a");
        MediaRecorder mr = null;
        clipActive = true;
        updateNotification(getString(R.string.kcam_vision_recording));
        try {
            mr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mr.setAudioSamplingRate(44100);
            mr.setAudioEncodingBitRate(128_000);
            mr.setOutputFile(out.getAbsolutePath());
            mr.prepare();
            mr.start();
            long started = SystemClock.elapsedRealtime();
            long lastLoud = started;
            long silenceMs = AudioVisionPrefs.silenceSec(this) * 1000L;
            while (loop) {
                sleep(250);
                int amp = mr.getMaxAmplitude(); // 0..32767 since last call
                int db = (int) Math.max(0, 20 * Math.log10(Math.max(amp, 1) / 32768.0) + 90);
                lastDb = db;
                long now = SystemClock.elapsedRealtime();
                if (db >= AudioVisionPrefs.thresholdDb(this) - 5) lastLoud = now;
                if (now - lastLoud >= silenceMs || now - started >= MAX_CLIP_MS) break;
            }
        } catch (Exception e) {
            FLog.w(TAG, "Audio clip failed", e);
        } finally {
            if (mr != null) {
                try {
                    mr.stop();
                } catch (RuntimeException e) {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                }
                mr.release();
            }
            clipActive = false;
            updateNotification(getString(R.string.kcam_vision_listening));
        }
    }

    private void recordCameraClip(QuickMode mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(this)) {
            FLog.w(TAG, "Camera action needs overlay permission; recording audio instead");
            recordAudioClip();
            return;
        }
        clipActive = true;
        updateNotification(getString(R.string.kcam_vision_recording));
        try {
            startActivity(new Intent(this, QuickRecordActivity.class)
                    .putExtra(QuickRecordActivity.EXTRA_MODE, mode.name())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            long until = SystemClock.elapsedRealtime() + AudioVisionPrefs.clipMin(this) * 60_000L;
            sleep(4000); // give the camera time to start
            while (loop && SystemClock.elapsedRealtime() < until && Recorders.isCameraRecording(this)) sleep(1000);
            if (Recorders.isCameraRecording(this)) Recorders.stopAll(this);
            // wait for the camera to release the mic
            for (int i = 0; i < 20 && Recorders.isCameraRecording(this); i++) sleep(500);
        } catch (Exception e) {
            FLog.w(TAG, "Camera clip failed", e);
        } finally {
            clipActive = false;
            updateNotification(getString(R.string.kcam_vision_listening));
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ── notification ────────────────────────────────────────────────

    private Notification notification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.audio_vision_title),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent stop = PendingIntent.getService(this, 4,
                new Intent(this, AudioVisionService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(getString(R.string.audio_vision_title))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, getString(R.string.kcam_stop), stop)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null || !running) return;
        try {
            nm.notify(NOTIF_ID, notification(text));
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        loop = false;
        running = false;
        if (worker != null) worker.interrupt();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
