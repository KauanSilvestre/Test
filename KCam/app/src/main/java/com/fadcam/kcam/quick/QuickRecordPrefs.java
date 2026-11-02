package com.fadcam.kcam.quick;

import android.content.Context;
import android.content.SharedPreferences;

public final class QuickRecordPrefs {

    private static final String PREFS = "kcam_quick";
    private static final String KEY_MODE = "mode";
    private static final String KEY_POWER_WATCH = "power_watch";
    private static final String KEY_TOGGLE_STOPS = "toggle_stops";
    private static final String KEY_VIBRATE = "vibrate";

    private QuickRecordPrefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static QuickMode mode(Context c) {
        return QuickMode.parse(p(c).getString(KEY_MODE, QuickMode.ASK.name()));
    }

    public static void setMode(Context c, QuickMode m) {
        p(c).edit().putString(KEY_MODE, m.name()).apply();
    }

    /** Experimental: detect double power press via fast screen off/on toggles. */
    public static boolean powerWatch(Context c) {
        return p(c).getBoolean(KEY_POWER_WATCH, false);
    }

    public static void setPowerWatch(Context c, boolean on) {
        p(c).edit().putBoolean(KEY_POWER_WATCH, on).apply();
    }

    /** When something is already recording, triggering again stops everything. */
    public static boolean toggleStops(Context c) {
        return p(c).getBoolean(KEY_TOGGLE_STOPS, true);
    }

    public static void setToggleStops(Context c, boolean on) {
        p(c).edit().putBoolean(KEY_TOGGLE_STOPS, on).apply();
    }

    public static boolean vibrate(Context c) {
        return p(c).getBoolean(KEY_VIBRATE, true);
    }

    public static void setVibrate(Context c, boolean on) {
        p(c).edit().putBoolean(KEY_VIBRATE, on).apply();
    }
}
