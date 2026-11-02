package com.fadcam.kcam.thermal;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.fadcam.FLog;
import com.fadcam.R;
import com.fadcam.kcam.quick.Recorders;

/**
 * Thermal Guardian: watches battery temperature and the system thermal status while
 * anything is recording. It warns when the phone gets hot and, if enabled, stops the
 * recording cleanly before Android throttles or kills it (a clean stop finalizes the file;
 * a thermal shutdown may not).
 *
 * <p>Installed once from the Application; listeners live as long as the app process,
 * which is kept alive by the recording service itself.
 */
public final class ThermalGuardian {

    private static final String TAG = "KCamThermal";
    private static final String PREFS = "kcam_thermal";
    private static final String CHANNEL = "kcam_thermal";
    private static final int NOTIF_ID = 77_700;
    private static final long WARN_INTERVAL_MS = 5 * 60_000L;

    private static volatile float lastTempC = Float.NaN;
    private static volatile int lastStatus = -1;
    private static long lastWarn;
    private static long lastStop;
    private static boolean installed;

    private ThermalGuardian() {
    }

    // ── settings ────────────────────────────────────────────────────

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) { return p(c).getBoolean("enabled", true); }
    public static void setEnabled(Context c, boolean v) { p(c).edit().putBoolean("enabled", v).apply(); }
    public static boolean autoStop(Context c) { return p(c).getBoolean("auto_stop", true); }
    public static void setAutoStop(Context c, boolean v) { p(c).edit().putBoolean("auto_stop", v).apply(); }
    public static int warnTemp(Context c) { return p(c).getInt("warn_c", 43); }
    public static void setWarnTemp(Context c, int v) { p(c).edit().putInt("warn_c", v).apply(); }
    public static int stopTemp(Context c) { return p(c).getInt("stop_c", 48); }
    public static void setStopTemp(Context c, int v) { p(c).edit().putInt("stop_c", v).apply(); }

    public static float lastTemperatureC() { return lastTempC; }
    public static int lastThermalStatus() { return lastStatus; }

    // ── wiring ──────────────────────────────────────────────────────

    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        Context app = context.getApplicationContext();
        BroadcastReceiver battery = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                if (t != Integer.MIN_VALUE) {
                    lastTempC = t / 10f;
                    evaluate(app);
                }
            }
        };
        Intent sticky = ContextCompat.registerReceiver(app, battery,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        if (sticky != null) battery.onReceive(app, sticky);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                lastStatus = pm.getCurrentThermalStatus();
                pm.addThermalStatusListener(ContextCompat.getMainExecutor(app), status -> {
                    lastStatus = status;
                    evaluate(app);
                });
            }
        }
    }

    /** Decision logic, separated for testing. 0 = ok, 1 = warn, 2 = stop. */
    static int decide(float tempC, int thermalStatus, int warnC, int stopC) {
        boolean critical = !Float.isNaN(tempC) && tempC >= stopC
                || thermalStatus >= 4; // PowerManager.THERMAL_STATUS_CRITICAL
        if (critical) return 2;
        boolean hot = !Float.isNaN(tempC) && tempC >= warnC
                || thermalStatus >= 3; // THERMAL_STATUS_SEVERE
        return hot ? 1 : 0;
    }

    private static void evaluate(Context c) {
        if (!enabled(c)) return;
        boolean recording;
        try {
            recording = Recorders.isAnythingRecording(c);
        } catch (Exception e) {
            return;
        }
        if (!recording) return;
        int level = decide(lastTempC, lastStatus, warnTemp(c), stopTemp(c));
        long now = SystemClock.elapsedRealtime();
        if (level == 2 && autoStop(c) && now - lastStop > 60_000) {
            lastStop = now;
            FLog.w(TAG, "Critical temperature " + lastTempC + "°C / status " + lastStatus + " → stopping");
            Recorders.stopAll(c);
            notify(c, c.getString(R.string.kcam_thermal_stopped, Math.round(lastTempC)));
        } else if (level >= 1 && now - lastWarn > WARN_INTERVAL_MS) {
            lastWarn = now;
            notify(c, c.getString(R.string.kcam_thermal_warning, Math.round(lastTempC)));
        }
    }

    private static void notify(Context c, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    c.getString(R.string.thermal_guardian_title), NotificationManager.IMPORTANCE_HIGH));
        }
        try {
            nm.notify(NOTIF_ID, new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(c.getString(R.string.thermal_guardian_title))
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .build());
        } catch (SecurityException ignored) {
        }
    }
}
