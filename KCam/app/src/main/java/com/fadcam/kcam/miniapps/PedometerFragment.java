package com.fadcam.kcam.miniapps;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Step counter backed by the hardware step sensor (no background service, no battery
 * cost while closed). "Today" is tracked by remembering the sensor's cumulative value at
 * the first reading of each day.
 */
public class PedometerFragment extends KMiniAppFragment implements SensorEventListener {

    private static final String PREFS = "kcam_miniapps";
    private static final float STEP_LENGTH_M = 0.75f;
    private static final float KCAL_PER_STEP = 0.04f;

    private SensorManager sm;
    private SharedPreferences prefs;
    private TextView steps, todayTv, distTv, kcalTv;
    private float sessionStart = -1;
    private int detectorSteps;
    private boolean usingDetector;

    @Override
    protected int titleRes() {
        return R.string.mini_app_pedometer_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        sm = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        icon(body, "directions_walk", 64, COLOR_ACCENT);
        steps = bigValue(body);
        steps.setText("0");
        text(body, getString(R.string.kcam_pedo_session), 14, COLOR_MUTED, false)
                .setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout c = card(body);
        todayTv = statRow(c, R.string.kcam_pedo_today);
        distTv = statRow(c, R.string.kcam_speedo_distance);
        kcalTv = statRow(c, R.string.kcam_pedo_kcal);
        text(c, getString(R.string.kcam_pedo_hint), 12, COLOR_MUTED, false);
        button(body, R.string.kcam_reset, v -> {
            sessionStart = -1;
            detectorSteps = 0;
            render(0, -1);
        });
    }

    @Override
    protected void onToolResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            withPermissions(this::startSensor, Manifest.permission.ACTIVITY_RECOGNITION);
        } else {
            startSensor();
        }
    }

    private void startSensor() {
        if (sm == null || !isResumed()) return;
        Sensor counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (counter != null) {
            usingDetector = false;
            sm.registerListener(this, counter, SensorManager.SENSOR_DELAY_UI);
            return;
        }
        Sensor detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (detector != null) {
            usingDetector = true;
            sm.registerListener(this, detector, SensorManager.SENSOR_DELAY_UI);
            return;
        }
        steps.setText(R.string.kcam_sensor_missing);
    }

    @Override
    protected void onToolPause() {
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (usingDetector) {
            detectorSteps++;
            render(detectorSteps, -1);
            return;
        }
        float total = e.values[0];
        if (sessionStart < 0) sessionStart = total;
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String key = "pedo_base_" + today;
        float base = prefs.getFloat(key, -1f);
        if (base < 0 || base > total) { // first reading today, or the counter reset after reboot
            base = total;
            prefs.edit().putFloat(key, base).apply();
        }
        render((int) (total - sessionStart), (int) (total - base));
    }

    private void render(int session, int today) {
        steps.setText(String.valueOf(session));
        int ref = today >= 0 ? today : session;
        todayTv.setText(today >= 0 ? String.valueOf(today) : "—");
        distTv.setText(String.format(Locale.US, "%.2f km", ref * STEP_LENGTH_M / 1000f));
        kcalTv.setText(String.format(Locale.US, "%.0f kcal", ref * KCAL_PER_STEP));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
