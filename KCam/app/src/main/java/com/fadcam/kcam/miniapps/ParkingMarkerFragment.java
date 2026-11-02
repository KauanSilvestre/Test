package com.fadcam.kcam.miniapps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.text.format.DateUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fadcam.R;

import java.util.Locale;

/**
 * Save where you parked and walk back to it: distance plus an arrow that points at the
 * car, combining GPS bearing with the compass heading. Fully offline; "Open in maps"
 * hands the coordinates to whatever maps app is installed.
 */
public class ParkingMarkerFragment extends KMiniAppFragment implements LocationListener {

    private static final String PREFS = "kcam_miniapps";
    private static final String K_LAT = "park_lat", K_LON = "park_lon", K_TIME = "park_time", K_NOTE = "park_note";

    private LocationManager lm;
    private SharedPreferences prefs;
    private HeadingTracker heading;
    private DialView arrow;
    private TextView distance, savedTv, accuracyTv;
    private Location current;
    private float headingDeg;

    @Override
    protected int titleRes() {
        return R.string.mini_app_parking_marker_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        arrow = new DialView(requireContext());
        arrow.setMode(DialView.Mode.ARROW);
        addView(body, arrow, 260);
        distance = bigValue(body);
        LinearLayout c = card(body);
        savedTv = statRow(c, R.string.kcam_park_saved);
        accuracyTv = statRow(c, R.string.kcam_speedo_gps);
        accuracyTv.setText(R.string.kcam_speedo_waiting);
        button(body, R.string.kcam_park_save, v -> save());
        button(body, R.string.kcam_park_open_maps, v -> openMaps());
        button(body, R.string.kcam_park_clear, v -> {
            prefs.edit().remove(K_LAT).remove(K_LON).remove(K_TIME).remove(K_NOTE).apply();
            render();
        });
        heading = new HeadingTracker(requireContext(), (az, p, r, acc) -> {
            headingDeg = az;
            render();
        });
        render();
    }

    private boolean hasSpot() {
        return prefs.contains(K_LAT) && prefs.contains(K_LON);
    }

    private Location spot() {
        Location l = new Location("saved");
        l.setLatitude(Double.longBitsToDouble(prefs.getLong(K_LAT, 0)));
        l.setLongitude(Double.longBitsToDouble(prefs.getLong(K_LON, 0)));
        return l;
    }

    private void save() {
        if (current == null) {
            Toast.makeText(requireContext(), R.string.kcam_speedo_waiting, Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
                .putLong(K_LAT, Double.doubleToRawLongBits(current.getLatitude()))
                .putLong(K_LON, Double.doubleToRawLongBits(current.getLongitude()))
                .putLong(K_TIME, System.currentTimeMillis())
                .apply();
        Toast.makeText(requireContext(), R.string.kcam_park_saved_toast, Toast.LENGTH_SHORT).show();
        render();
    }

    private void openMaps() {
        if (!hasSpot()) return;
        Location s = spot();
        String q = String.format(Locale.US, "%.6f,%.6f", s.getLatitude(), s.getLongitude());
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + q + "?q=" + q + "(" + Uri.encode(getString(R.string.mini_app_parking_marker_title)) + ")"));
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.kcam_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void render() {
        if (!hasSpot()) {
            distance.setText("—");
            savedTv.setText(R.string.kcam_park_none);
            arrow.setArrow(0);
            return;
        }
        long t = prefs.getLong(K_TIME, 0);
        savedTv.setText(DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        if (current == null) {
            distance.setText("…");
            return;
        }
        Location s = spot();
        float d = current.distanceTo(s);
        distance.setText(d < 1000 ? String.format(Locale.US, "%.0f m", d) : String.format(Locale.US, "%.2f km", d / 1000f));
        float bearing = current.bearingTo(s); // -180..180 from true north
        arrow.setArrow(bearing - headingDeg);
    }

    @Override
    protected void onToolResume() {
        heading.start();
        withPermissions(this::startGps, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @SuppressLint("MissingPermission")
    private void startGps() {
        if (lm == null || !isResumed()) return;
        try {
            Location lastKnown = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown != null && System.currentTimeMillis() - lastKnown.getTime() < 120_000) current = lastKnown;
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, this, Looper.getMainLooper());
        } catch (Exception e) {
            accuracyTv.setText(R.string.kcam_speedo_gps_off);
        }
        render();
    }

    @Override
    protected void onToolPause() {
        heading.stop();
        if (lm != null) lm.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(@NonNull Location loc) {
        current = loc;
        accuracyTv.setText(loc.hasAccuracy() ? String.format(Locale.US, "±%.0f m", loc.getAccuracy()) : "—");
        render();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        accuracyTv.setText(R.string.kcam_speedo_gps_off);
    }
}
