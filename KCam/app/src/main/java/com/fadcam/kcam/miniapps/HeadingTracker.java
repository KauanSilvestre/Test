package com.fadcam.kcam.miniapps;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Device heading (azimuth, degrees clockwise from magnetic north), smoothed.
 * Uses the rotation-vector sensor when present, falling back to accelerometer +
 * magnetometer. Also exposes pitch/roll for tools that need tilt.
 */
public class HeadingTracker implements SensorEventListener {

    public interface Listener {
        void onHeading(float azimuthDeg, float pitchDeg, float rollDeg, int accuracy);
    }

    private final SensorManager sm;
    private final Context context;
    private final Listener listener;
    private final float[] rotation = new float[9];
    private final float[] remapped = new float[9];
    private final float[] orientation = new float[3];
    private final float[] gravity = new float[3];
    private final float[] geomagnetic = new float[3];
    private boolean hasGravity, hasGeomagnetic;
    private float smoothAzimuth = Float.NaN;
    private int accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;

    public HeadingTracker(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;
    }

    /** @return false when the device has no usable heading sensors. */
    public boolean start() {
        if (sm == null) return false;
        Sensor rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rv != null) {
            sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI);
            return true;
        }
        Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (acc == null || mag == null) return false;
        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
        sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI);
        return true;
    }

    public void stop() {
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        switch (e.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector(rotation, e.values);
                publish();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                lowPass(e.values, gravity);
                hasGravity = true;
                if (hasGeomagnetic && SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) publish();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                lowPass(e.values, geomagnetic);
                hasGeomagnetic = true;
                break;
            default:
                break;
        }
    }

    private void publish() {
        int axisX = SensorManager.AXIS_X, axisY = SensorManager.AXIS_Y;
        switch (displayRotation()) {
            case Surface.ROTATION_90:
                axisX = SensorManager.AXIS_Y;
                axisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                axisX = SensorManager.AXIS_MINUS_X;
                axisY = SensorManager.AXIS_MINUS_Y;
                break;
            case Surface.ROTATION_270:
                axisX = SensorManager.AXIS_MINUS_Y;
                axisY = SensorManager.AXIS_X;
                break;
            default:
                break;
        }
        SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped);
        SensorManager.getOrientation(remapped, orientation);
        float az = (float) ((Math.toDegrees(orientation[0]) + 360.0) % 360.0);
        if (Float.isNaN(smoothAzimuth)) {
            smoothAzimuth = az;
        } else {
            float diff = ((az - smoothAzimuth + 540f) % 360f) - 180f; // shortest path
            smoothAzimuth = (smoothAzimuth + diff * 0.15f + 360f) % 360f;
        }
        listener.onHeading(smoothAzimuth,
                (float) Math.toDegrees(orientation[1]),
                (float) Math.toDegrees(orientation[2]),
                accuracy);
    }

    @SuppressWarnings("deprecation")
    private int displayRotation() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return wm != null ? wm.getDefaultDisplay().getRotation() : Surface.ROTATION_0;
    }

    private static void lowPass(float[] in, float[] out) {
        final float a = 0.2f;
        for (int i = 0; i < 3; i++) out[i] = out[i] + a * (in[i] - out[i]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int acc) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD || sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            accuracy = acc;
        }
    }

    public static String cardinal(float deg) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[Math.round(((deg % 360) + 360) % 360 / 45f) % 8];
    }
}
