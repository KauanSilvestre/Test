package com.fadcam.kcam.schedule;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * One scheduled recording: start at HH:MM on the selected weekdays (or once), record for
 * {@link #durationMinutes}, using the given camera mode. Pure Java so the timing logic is
 * unit-testable without Android.
 */
public final class RecordingSchedule {

    /** Bit for each weekday, Monday = bit 0 … Sunday = bit 6. 0 means "once". */
    public static final int MON = 1, TUE = 1 << 1, WED = 1 << 2, THU = 1 << 3, FRI = 1 << 4,
            SAT = 1 << 5, SUN = 1 << 6, EVERY_DAY = 0x7F;

    public final int id;
    public final int hour;
    public final int minute;
    public final int daysMask;
    public final int durationMinutes;
    /** One of RecordingStartActivity.CAMERA_MODE_* ("back", "front", "current", "dual"). */
    public final String cameraMode;
    public final boolean enabled;

    public RecordingSchedule(int id, int hour, int minute, int daysMask, int durationMinutes,
                             String cameraMode, boolean enabled) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new IllegalArgumentException("time");
        if (durationMinutes < 1) throw new IllegalArgumentException("duration");
        this.id = id;
        this.hour = hour;
        this.minute = minute;
        this.daysMask = daysMask & EVERY_DAY;
        this.durationMinutes = durationMinutes;
        this.cameraMode = cameraMode == null ? "current" : cameraMode;
        this.enabled = enabled;
    }

    public boolean isOneShot() {
        return daysMask == 0;
    }

    public RecordingSchedule withEnabled(boolean on) {
        return new RecordingSchedule(id, hour, minute, daysMask, durationMinutes, cameraMode, on);
    }

    /** Monday-based bit for a {@link Calendar#DAY_OF_WEEK} value. */
    static int bitFor(int calendarDayOfWeek) {
        // Calendar: SUNDAY=1 … SATURDAY=7  →  MON=0 … SUN=6
        return 1 << ((calendarDayOfWeek + 5) % 7);
    }

    /**
     * Next start time strictly after {@code nowMillis}, or -1 if none (should not happen for
     * valid schedules — a one-shot always has a next occurrence within 24 h).
     */
    public long nextStartAfter(long nowMillis, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(nowMillis);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            if (c.getTimeInMillis() > nowMillis
                    && (daysMask == 0 || (daysMask & bitFor(c.get(Calendar.DAY_OF_WEEK))) != 0)) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
            // re-pin the wall-clock time in case a DST shift moved it
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
        }
        return -1;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id).put("h", hour).put("m", minute).put("days", daysMask)
                .put("dur", durationMinutes).put("cam", cameraMode).put("on", enabled);
    }

    public static RecordingSchedule fromJson(JSONObject o) throws JSONException {
        return new RecordingSchedule(o.getInt("id"), o.getInt("h"), o.getInt("m"),
                o.optInt("days", 0), o.optInt("dur", 30), o.optString("cam", "current"),
                o.optBoolean("on", true));
    }
}
