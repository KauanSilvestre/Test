package com.fadcam.kcam.miniapps;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.R;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live readout of every hardware sensor the device reports. */
public class SensorDashboardFragment extends KMiniAppFragment implements SensorEventListener {

    private static final long UI_INTERVAL_MS = 150;

    private SensorManager sm;
    private final Map<Sensor, TextView> values = new HashMap<>();
    private final Map<Sensor, Long> lastUi = new HashMap<>();

    @Override
    protected int titleRes() {
        return R.string.mini_app_sensor_dashboard_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        values.clear();
        lastUi.clear();
        sm = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return;
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        text(body, getString(R.string.kcam_sensors_count, sensors.size()), 13, COLOR_MUTED, false);
        for (Sensor s : sensors) {
            if (s.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) continue;
            LinearLayout c = card(body);
            text(c, s.getName(), 15, 0xFFFFFFFF, true);
            text(c, String.format(Locale.US, "%s · %s · max %.1f · %.2f mA",
                    typeName(s), s.getVendor(), s.getMaximumRange(), s.getPower()), 11, COLOR_MUTED, false);
            TextView v = text(c, "—", 14, COLOR_OK, false);
            v.setTypeface(android.graphics.Typeface.MONOSPACE);
            values.put(s, v);
        }
    }

    private static String typeName(Sensor s) {
        String t = s.getStringType();
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }

    @Override
    protected void onToolResume() {
        for (Sensor s : values.keySet()) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onToolPause() {
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        TextView tv = values.get(e.sensor);
        if (tv == null) return;
        long now = System.currentTimeMillis();
        Long last = lastUi.get(e.sensor);
        if (last != null && now - last < UI_INTERVAL_MS) return;
        lastUi.put(e.sensor, now);
        StringBuilder sb = new StringBuilder();
        int n = Math.min(e.values.length, 6);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append("   ");
            sb.append(String.format(Locale.US, "%.3f", e.values[i]));
        }
        tv.setText(sb.toString());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
