package com.fadcam.kcam.miniapps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.R;

import java.util.Locale;

/** GPS speedometer with max / average speed, trip distance and a km/h ↔ mph toggle. */
public class SpeedometerFragment extends KMiniAppFragment implements LocationListener {

    private static final String PREFS = "kcam_miniapps";
    private static final String KEY_MPH = "speedo_mph";

    private LocationManager lm;
    private DialView gauge;
    private TextView speed, unitTv, maxTv, avgTv, distTv, gpsTv;
    private Location last;
    private double distanceM, maxMs;
    private long movingMs, lastTime;
    private boolean mph;

    @Override
    protected int titleRes() {
        return R.string.mini_app_speedometer_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        mph = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MPH, false);
        gauge = new DialView(requireContext());
        gauge.setMode(DialView.Mode.GAUGE);
        addView(body, gauge, 240);
        speed = bigValue(body);
        unitTv = text(body, "", 16, COLOR_MUTED, true);
        unitTv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout c = card(body);
        maxTv = statRow(c, R.string.kcam_stat_max);
        avgTv = statRow(c, R.string.kcam_stat_avg);
        distTv = statRow(c, R.string.kcam_speedo_distance);
        gpsTv = statRow(c, R.string.kcam_speedo_gps);
        gpsTv.setText(R.string.kcam_speedo_waiting);
        button(body, R.string.kcam_speedo_units, v -> {
            mph = !mph;
            requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MPH, mph).apply();
            render(last != null && last.hasSpeed() ? last.getSpeed() : 0);
        });
        button(body, R.string.kcam_reset, v -> {
            distanceM = 0;
            maxMs = 0;
            movingMs = 0;
            render(last != null && last.hasSpeed() ? last.getSpeed() : 0);
        });
        render(0);
    }

    @Override
    protected void onToolResume() {
        withPermissions(this::startGps, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @SuppressLint("MissingPermission")
    private void startGps() {
        if (lm == null || !isResumed()) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0f, this, Looper.getMainLooper());
        } catch (Exception e) {
            gpsTv.setText(R.string.kcam_speedo_gps_off);
        }
    }

    @Override
    protected void onToolPause() {
        if (lm != null) lm.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(@NonNull Location loc) {
        float ms = loc.hasSpeed() ? loc.getSpeed() : 0f;
        if (last != null) {
            float d = last.distanceTo(loc);
            // ignore jitter while standing still / with poor accuracy
            if (ms > 0.7f && d < 200 && (!loc.hasAccuracy() || loc.getAccuracy() < 30)) {
                distanceM += d;
                movingMs += loc.getTime() - lastTime;
            }
        }
        last = loc;
        lastTime = loc.getTime();
        maxMs = Math.max(maxMs, ms);
        gpsTv.setText(loc.hasAccuracy() ? String.format(Locale.US, "±%.0f m", loc.getAccuracy()) : "—");
        render(ms);
    }

    private void render(float ms) {
        double f = mph ? 2.236936 : 3.6;
        String u = mph ? "mph" : "km/h";
        speed.setText(String.format(Locale.US, "%.0f", ms * f));
        unitTv.setText(u);
        maxTv.setText(String.format(Locale.US, "%.1f %s", maxMs * f, u));
        double avg = movingMs > 0 ? distanceM / (movingMs / 1000.0) : 0;
        avgTv.setText(String.format(Locale.US, "%.1f %s", avg * f, u));
        distTv.setText(mph ? String.format(Locale.US, "%.2f mi", distanceM / 1609.344)
                : String.format(Locale.US, "%.2f km", distanceM / 1000.0));
        gauge.setFraction((float) (ms * 3.6 / 200.0));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        gpsTv.setText(R.string.kcam_speedo_waiting);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        gpsTv.setText(R.string.kcam_speedo_gps_off);
    }
}
