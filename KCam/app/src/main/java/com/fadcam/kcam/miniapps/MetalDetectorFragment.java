package com.fadcam.kcam.miniapps;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.HapticFeedbackConstants;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.R;

import java.util.Locale;

/**
 * Magnetometer-based metal detector. Shows field strength (µT) and the deviation from a
 * calibrated baseline; buzzes (haptic) when deviation passes the threshold. Only detects
 * ferromagnetic objects close to the sensor (usually near the top of the phone).
 */
public class MetalDetectorFragment extends KMiniAppFragment implements SensorEventListener {

    private static final float THRESHOLD_UT = 15f;
    private static final float FULL_SCALE_UT = 120f;

    private SensorManager sm;
    private DialView gauge;
    private TextView value, delta, status, peakTv;
    private float smooth = Float.NaN;
    private float baseline = Float.NaN;
    private float peak;
    private long lastBuzz;

    @Override
    protected int titleRes() {
        return R.string.mini_app_metal_detector_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        sm = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        gauge = new DialView(requireContext());
        gauge.setMode(DialView.Mode.GAUGE);
        addView(body, gauge, 240);
        value = bigValue(body);
        status = text(body, "", 16, COLOR_MUTED, true);
        status.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout c = card(body);
        delta = statRow(c, R.string.kcam_metal_delta);
        peakTv = statRow(c, R.string.kcam_metal_peak);
        text(c, getString(R.string.kcam_metal_hint), 12, COLOR_MUTED, false);
        button(body, R.string.kcam_metal_calibrate, v -> {
            baseline = smooth;
            peak = 0;
        });
    }

    @Override
    protected void onToolResume() {
        Sensor mag = sm != null ? sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) : null;
        if (mag == null) {
            value.setText(R.string.kcam_sensor_missing);
            return;
        }
        sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onToolPause() {
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float m = (float) Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]);
        smooth = Float.isNaN(smooth) ? m : smooth + 0.25f * (m - smooth);
        if (Float.isNaN(baseline)) baseline = smooth;
        float d = Math.abs(smooth - baseline);
        peak = Math.max(peak, d);
        value.setText(String.format(Locale.US, "%.0f µT", smooth));
        delta.setText(String.format(Locale.US, "%.1f µT", d));
        peakTv.setText(String.format(Locale.US, "%.1f µT", peak));
        gauge.setFraction(d / FULL_SCALE_UT);
        boolean hit = d > THRESHOLD_UT;
        gauge.setAccentColor(hit ? COLOR_ACCENT : COLOR_OK);
        status.setText(hit ? R.string.kcam_metal_detected : R.string.kcam_metal_none);
        status.setTextColor(hit ? COLOR_ACCENT : COLOR_MUTED);
        long now = System.currentTimeMillis();
        if (hit && now - lastBuzz > Math.max(80, 600 - (long) (d * 4)) && getView() != null) {
            getView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            lastBuzz = now;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
