package com.fadcam.kcam.miniapps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.R;

import java.util.Locale;

/**
 * Sound level meter. Computes RMS of raw PCM and converts to dBFS; the dB value shown is
 * an estimate (dBFS + calibration offset), because phone microphones are not calibrated
 * and apply their own gain. Good for relative comparisons, not certified measurements.
 */
public class SoundMeterFragment extends KMiniAppFragment {

    private static final int SAMPLE_RATE = 44100;
    /** Typical offset mapping full-scale digital level to approximate dB SPL on phones. */
    private static final double CALIBRATION_DB = 90.0;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Thread worker;
    private DialView gauge;
    private TextView value, level, minTv, maxTv, avgTv;
    private double min = Double.MAX_VALUE, max, sum;
    private long count;

    @Override
    protected int titleRes() {
        return R.string.mini_app_sound_meter_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        gauge = new DialView(requireContext());
        gauge.setMode(DialView.Mode.GAUGE);
        addView(body, gauge, 240);
        value = bigValue(body);
        level = text(body, "", 16, COLOR_MUTED, true);
        level.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout c = card(body);
        minTv = statRow(c, R.string.kcam_stat_min);
        avgTv = statRow(c, R.string.kcam_stat_avg);
        maxTv = statRow(c, R.string.kcam_stat_max);
        text(c, getString(R.string.kcam_sound_hint), 12, COLOR_MUTED, false);
        button(body, R.string.kcam_reset, v -> resetStats());
    }

    private void resetStats() {
        min = Double.MAX_VALUE;
        max = 0;
        sum = 0;
        count = 0;
    }

    @Override
    protected void onToolResume() {
        withPermissions(this::startCapture, Manifest.permission.RECORD_AUDIO);
    }

    @Override
    protected void onToolPause() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    @SuppressLint("MissingPermission") // checked by withPermissions()
    private void startCapture() {
        if (running || !isResumed()) return;
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) {
            value.setText(R.string.kcam_sensor_missing);
            return;
        }
        final int bufSize = Math.max(minBuf, SAMPLE_RATE / 10 * 2);
        running = true;
        worker = new Thread(() -> {
            AudioRecord rec = null;
            try {
                rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
                if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                    main.post(() -> value.setText(R.string.kcam_mic_busy));
                    return;
                }
                rec.startRecording();
                short[] buf = new short[bufSize / 2];
                while (running) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    double acc = 0;
                    for (int i = 0; i < n; i++) acc += (double) buf[i] * buf[i];
                    double rms = Math.sqrt(acc / n);
                    double dbfs = 20 * Math.log10(Math.max(rms, 1.0) / 32768.0);
                    final double db = Math.max(0, dbfs + CALIBRATION_DB);
                    main.post(() -> show(db));
                }
            } catch (Exception e) {
                main.post(() -> {
                    if (isAdded()) value.setText(R.string.kcam_mic_busy);
                });
            } finally {
                if (rec != null) {
                    try {
                        rec.stop();
                    } catch (Exception ignored) {
                    }
                    rec.release();
                }
            }
        }, "KCamSoundMeter");
        worker.start();
    }

    private void show(double db) {
        if (!isAdded() || !running) return;
        min = Math.min(min, db);
        max = Math.max(max, db);
        sum += db;
        count++;
        value.setText(String.format(Locale.US, "%.0f dB", db));
        minTv.setText(String.format(Locale.US, "%.0f dB", min));
        maxTv.setText(String.format(Locale.US, "%.0f dB", max));
        avgTv.setText(String.format(Locale.US, "%.0f dB", sum / count));
        gauge.setFraction((float) (db / 120.0));
        int res;
        if (db < 40) res = R.string.kcam_sound_quiet;
        else if (db < 60) res = R.string.kcam_sound_normal;
        else if (db < 80) res = R.string.kcam_sound_loud;
        else res = R.string.kcam_sound_very_loud;
        level.setText(res);
        gauge.setAccentColor(db < 80 ? COLOR_OK : COLOR_ACCENT);
    }
}
