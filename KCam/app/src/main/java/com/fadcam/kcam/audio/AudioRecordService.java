package com.fadcam.kcam.audio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.fadcam.Constants;
import com.fadcam.FLog;
import com.fadcam.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FadMic: audio-only background recorder (foreground service, type microphone).
 * Records AAC in an .m4a container into the app's recordings folder under "Audio".
 * Keeps running with the screen off; stop from the notification, the FadMic screen,
 * a quick action, or {@link #stop(Context)}.
 */
public class AudioRecordService extends Service {

    public static final String ACTION_START = "com.fadcam.kcam.audio.START";
    public static final String ACTION_STOP = "com.fadcam.kcam.audio.STOP";
    public static final String ACTION_STATE = "com.fadcam.kcam.audio.STATE";
    public static final String EXTRA_RUNNING = "running";

    private static final String TAG = "KCamFadMic";
    private static final String CHANNEL = "kcam_fadmic";
    private static final int NOTIF_ID = 77_500;

    private static volatile boolean running;
    private static volatile long startedAtElapsed;
    private static volatile String currentPath;

    private MediaRecorder recorder;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            updateNotification();
            handler.postDelayed(this, 1000);
        }
    };

    public static boolean isRunning() {
        return running;
    }

    public static long elapsedMs() {
        return running ? SystemClock.elapsedRealtime() - startedAtElapsed : 0;
    }

    public static String currentPath() {
        return currentPath;
    }

    public static void start(Context context) {
        Intent i = new Intent(context, AudioRecordService.class).setAction(ACTION_START);
        ContextCompat.startForegroundService(context, i);
    }

    public static void stop(Context context) {
        if (!running) return;
        context.startService(new Intent(context, AudioRecordService.class).setAction(ACTION_STOP));
    }

    /** Folder that holds FadMic recordings (app-specific storage, no permission needed). */
    public static File audioDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File dir = new File(new File(base, Constants.RECORDING_DIRECTORY), "Audio");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopRecording();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : 0);
        } catch (Exception e) {
            FLog.e(TAG, "Cannot enter foreground (mic permission / background start?)", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!startRecording()) {
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private boolean startRecording() {
        String name = "FadMic_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".m4a";
        File out = new File(audioDir(this), name);
        try {
            recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(AudioPrefs.channels(this));
            recorder.setAudioSamplingRate(AudioPrefs.sampleRate(this));
            recorder.setAudioEncodingBitRate(AudioPrefs.bitrate(this));
            recorder.setOutputFile(out.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            FLog.e(TAG, "MediaRecorder failed to start", e);
            com.fadcam.Utils.vibrateRecordingError(this);
            releaseRecorder();
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return false;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KCam:FadMic");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(12 * 60 * 60 * 1000L); // safety cap: 12 h
        }
        currentPath = out.getAbsolutePath();
        startedAtElapsed = SystemClock.elapsedRealtime();
        running = true;
        broadcastState();
        // Honest tactile feedback: audio recording actually started.
        com.fadcam.Utils.vibrateRecordingStart(this);
        handler.post(tick);
        FLog.i(TAG, "FadMic recording → " + currentPath);
        return true;
    }

    private void stopRecording() {
        handler.removeCallbacks(tick);
        boolean wasRunning = running;
        running = false;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException e) {
                // stop() throws if nothing was captured: drop the empty file
                if (currentPath != null) //noinspection ResultOfMethodCallIgnored
                    new File(currentPath).delete();
            }
        }
        releaseRecorder();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wasRunning) {
            broadcastState();
            // Honest tactile feedback: audio recording actually stopped.
            com.fadcam.Utils.vibrateRecordingStop(this);
        }
        stopForegroundCompat();
        stopSelf();
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Exception ignored) {
            }
            recorder = null;
        }
    }

    private void stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    private void broadcastState() {
        sendBroadcast(new Intent(ACTION_STATE).setPackage(getPackageName()).putExtra(EXTRA_RUNNING, running));
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.kcam_fadmic_title),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AudioRecordService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent content = open == null ? null : PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long secs = elapsedMs() / 1000;
        String time = String.format(Locale.US, "%02d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(getString(R.string.kcam_fadmic_recording))
                .setContentText(time)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(content)
                .addAction(0, getString(R.string.kcam_stop), stop)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.notify(NOTIF_ID, buildNotification());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS denied: recording continues, notification just not refreshed
        }
    }

    @Override
    public void onDestroy() {
        if (running) stopRecording();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
