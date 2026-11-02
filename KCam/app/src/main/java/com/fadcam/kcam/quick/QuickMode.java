package com.fadcam.kcam.quick;

/** What a quick-record trigger does. */
public enum QuickMode {
    ASK, BACK, FRONT, AUDIO, SCREEN, ALL;

    public static QuickMode parse(String s) {
        if (s != null) {
            for (QuickMode m : values()) if (m.name().equalsIgnoreCase(s)) return m;
        }
        return ASK;
    }
}
