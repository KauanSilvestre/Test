package com.fadcam.kcam.audio;

import android.content.Context;
import android.content.SharedPreferences;

/** FadMic quality settings. */
public final class AudioPrefs {

    private static final String PREFS = "kcam_fadmic";
    public static final String KEY_QUALITY = "quality";

    /** 0 = voice (mono 22 kHz 64 kbps), 1 = standard (mono 44.1 kHz 128 kbps), 2 = high (stereo 48 kHz 256 kbps). */
    public static int quality(Context c) {
        return prefs(c).getInt(KEY_QUALITY, 1);
    }

    public static void setQuality(Context c, int q) {
        prefs(c).edit().putInt(KEY_QUALITY, Math.max(0, Math.min(2, q))).apply();
    }

    static int sampleRate(Context c) {
        switch (quality(c)) {
            case 0: return 22050;
            case 2: return 48000;
            default: return 44100;
        }
    }

    static int bitrate(Context c) {
        switch (quality(c)) {
            case 0: return 64_000;
            case 2: return 256_000;
            default: return 128_000;
        }
    }

    static int channels(Context c) {
        return quality(c) == 2 ? 2 : 1;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private AudioPrefs() {
    }
}
