package com.fadcam.kcam.vision;

import android.content.Context;
import android.content.SharedPreferences;

public final class AudioVisionPrefs {
    private static final String PREFS = "kcam_audio_vision";

    /** What to record when sound is detected. */
    public static final int ACTION_AUDIO = 0, ACTION_BACK = 1, ACTION_FRONT = 2;

    private AudioVisionPrefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Trigger level in approximate dB (see SoundMeter: dBFS + 90). */
    public static int thresholdDb(Context c) { return p(c).getInt("threshold_db", 65); }
    public static void setThresholdDb(Context c, int v) { p(c).edit().putInt("threshold_db", v).apply(); }
    public static int action(Context c) { return p(c).getInt("action", ACTION_AUDIO); }
    public static void setAction(Context c, int v) { p(c).edit().putInt("action", v).apply(); }
    /** Audio: stop after this many seconds of silence. */
    public static int silenceSec(Context c) { return p(c).getInt("silence_s", 15); }
    public static void setSilenceSec(Context c, int v) { p(c).edit().putInt("silence_s", v).apply(); }
    /** Camera: fixed clip length in minutes (the mic is busy, so silence cannot be measured). */
    public static int clipMin(Context c) { return p(c).getInt("clip_min", 2); }
    public static void setClipMin(Context c, int v) { p(c).edit().putInt("clip_min", v).apply(); }
}
