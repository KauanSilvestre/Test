package com.fadcam.kcam.miniapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.R;

import java.util.Locale;

/**
 * Clinometer / bubble level. Pitch and roll come from the gravity vector, so it works
 * with the phone flat (bubble level) or on its edge (slope angle). "Zero" stores an
 * offset so you can measure relative to any reference surface.
 */
public class ClinometerFragment extends KMiniAppFragment implements SensorEventListener {

    private SensorManager sm;
    private final float[] g = new float[3];
    private boolean hasG;
    private float pitch, roll, zeroPitch, zeroRoll;
    private TextView pitchTv, rollTv, slopeTv, angleTv;
    private BubbleView bubble;

    @Override
    protected int titleRes() {
        return R.string.mini_app_clinometer_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        sm = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        bubble = new BubbleView(requireContext());
        addView(body, bubble, 260);
        angleTv = bigValue(body);
        LinearLayout c = card(body);
        pitchTv = statRow(c, R.string.kcam_clino_pitch);
        rollTv = statRow(c, R.string.kcam_clino_roll);
        slopeTv = statRow(c, R.string.kcam_clino_slope);
        button(body, R.string.kcam_clino_zero, v -> {
            zeroPitch = pitch;
            zeroRoll = roll;
        });
        button(body, R.string.kcam_clino_reset, v -> {
            zeroPitch = 0;
            zeroRoll = 0;
        });
    }

    @Override
    protected void onToolResume() {
        Sensor s = sm != null ? sm.getDefaultSensor(Sensor.TYPE_GRAVITY) : null;
        if (s == null && sm != null) s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) {
            angleTv.setText(R.string.kcam_sensor_missing);
            return;
        }
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onToolPause() {
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        for (int i = 0; i < 3; i++) g[i] = hasG ? g[i] + 0.15f * (e.values[i] - g[i]) : e.values[i];
        hasG = true;
        double norm = Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);
        if (norm < 1e-3) return;
        pitch = (float) Math.toDegrees(Math.atan2(g[1], g[2]));
        roll = (float) Math.toDegrees(Math.atan2(-g[0], Math.sqrt(g[1] * g[1] + g[2] * g[2])));
        float p = wrap(pitch - zeroPitch), r = wrap(roll - zeroRoll);
        float tilt = (float) Math.toDegrees(Math.acos(Math.min(1.0, Math.abs(g[2]) / norm)));
        pitchTv.setText(String.format(Locale.US, "%.1f°", p));
        rollTv.setText(String.format(Locale.US, "%.1f°", r));
        double slope = Math.tan(Math.toRadians(Math.min(89.9, Math.abs(p)))) * 100.0;
        slopeTv.setText(String.format(Locale.US, "%.1f %%", slope));
        angleTv.setText(String.format(Locale.US, "%.1f°", (zeroPitch == 0 && zeroRoll == 0) ? tilt
                : (float) Math.hypot(p, r)));
        bubble.set(r, p);
    }

    private static float wrap(float d) {
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /** Circular spirit level; the bubble moves opposite to tilt, max ±45°. */
    static class BubbleView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bub = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x, y;

        BubbleView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0xFF3A3A3A);
            ring.setStrokeWidth(4f);
            bub.setColor(0xFF4CAF50);
        }

        void set(float rollDeg, float pitchDeg) {
            x = Math.max(-1f, Math.min(1f, rollDeg / 45f));
            y = Math.max(-1f, Math.min(1f, pitchDeg / 45f));
            bub.setColor(Math.hypot(rollDeg, pitchDeg) < 1.0 ? 0xFF4CAF50 : 0xFFE43C3C);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) * 0.9f;
            c.drawCircle(cx, cy, r, ring);
            c.drawCircle(cx, cy, r * 0.12f, ring);
            c.drawLine(cx - r, cy, cx + r, cy, ring);
            c.drawLine(cx, cy - r, cx, cy + r, ring);
            float br = r * 0.11f, max = r - br;
            c.drawCircle(cx - x * max, cy + y * max, br, bub);
        }
    }
}
