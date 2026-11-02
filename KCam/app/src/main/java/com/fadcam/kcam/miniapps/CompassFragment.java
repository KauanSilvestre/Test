package com.fadcam.kcam.miniapps;

import android.hardware.SensorManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.R;

import java.util.Locale;

/** Digital compass: rotating dial, bearing in degrees + cardinal, calibration hint. */
public class CompassFragment extends KMiniAppFragment {

    private HeadingTracker tracker;
    private DialView dial;
    private TextView heading;
    private TextView cardinal;
    private TextView hint;

    @Override
    protected int titleRes() {
        return R.string.mini_app_compass_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        dial = new DialView(requireContext());
        dial.setMode(DialView.Mode.COMPASS);
        addView(body, dial, 300);
        heading = bigValue(body);
        cardinal = text(body, "", 22, COLOR_MUTED, true);
        cardinal.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout c = card(body);
        hint = text(c, getString(R.string.kcam_compass_hint), 13, COLOR_MUTED, false);
        tracker = new HeadingTracker(requireContext(), (az, pitch, roll, accuracy) -> {
            dial.setHeading(az);
            heading.setText(String.format(Locale.US, "%.0f°", az));
            cardinal.setText(HeadingTracker.cardinal(az));
            boolean low = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW;
            hint.setText(low ? R.string.kcam_compass_calibrate : R.string.kcam_compass_hint);
            hint.setTextColor(low ? COLOR_ACCENT : COLOR_MUTED);
        });
    }

    @Override
    protected void onToolResume() {
        if (!tracker.start()) heading.setText(R.string.kcam_sensor_missing);
    }

    @Override
    protected void onToolPause() {
        tracker.stop();
    }
}
