package com.fadcam.kcam.schedule;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class RecordingScheduleTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** 2026-11-02 is a Monday. */
    private static long at(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void oneShot_laterToday() {
        RecordingSchedule s = new RecordingSchedule(1, 20, 0, 0, 10, "back", true);
        assertEquals(at(2026, 11, 2, 20, 0), s.nextStartAfter(at(2026, 11, 2, 19, 0), UTC));
    }

    @Test
    public void oneShot_alreadyPassed_goesToTomorrow() {
        RecordingSchedule s = new RecordingSchedule(1, 8, 30, 0, 10, "back", true);
        assertEquals(at(2026, 11, 3, 8, 30), s.nextStartAfter(at(2026, 11, 2, 9, 0), UTC));
    }

    @Test
    public void exactlyNow_isNotReturned() {
        RecordingSchedule s = new RecordingSchedule(1, 9, 0, RecordingSchedule.EVERY_DAY, 10, "back", true);
        assertEquals(at(2026, 11, 3, 9, 0), s.nextStartAfter(at(2026, 11, 2, 9, 0), UTC));
    }

    @Test
    public void weekdaysOnly_fromFridayEvening_isMonday() {
        int weekdays = RecordingSchedule.MON | RecordingSchedule.TUE | RecordingSchedule.WED
                | RecordingSchedule.THU | RecordingSchedule.FRI;
        RecordingSchedule s = new RecordingSchedule(1, 7, 0, weekdays, 30, "back", true);
        // Friday 2026-11-06 18:00 → Monday 2026-11-09 07:00
        assertEquals(at(2026, 11, 9, 7, 0), s.nextStartAfter(at(2026, 11, 6, 18, 0), UTC));
    }

    @Test
    public void sundayOnly() {
        RecordingSchedule s = new RecordingSchedule(1, 12, 0, RecordingSchedule.SUN, 30, "front", true);
        assertEquals(at(2026, 11, 8, 12, 0), s.nextStartAfter(at(2026, 11, 2, 12, 0), UTC));
    }

    @Test
    public void sameDayNextWeek_whenOnlyTodayAndPassed() {
        RecordingSchedule s = new RecordingSchedule(1, 6, 0, RecordingSchedule.MON, 30, "back", true);
        assertEquals(at(2026, 11, 9, 6, 0), s.nextStartAfter(at(2026, 11, 2, 6, 1), UTC));
    }

    @Test
    public void bitFor_mapsCalendarDays() {
        assertEquals(RecordingSchedule.MON, RecordingSchedule.bitFor(Calendar.MONDAY));
        assertEquals(RecordingSchedule.SAT, RecordingSchedule.bitFor(Calendar.SATURDAY));
        assertEquals(RecordingSchedule.SUN, RecordingSchedule.bitFor(Calendar.SUNDAY));
    }

    @Test
    public void jsonRoundTrip() throws Exception {
        RecordingSchedule s = new RecordingSchedule(7, 23, 59, RecordingSchedule.SAT | RecordingSchedule.SUN,
                120, "dual", false);
        JSONObject o = new JSONObject(s.toJson().toString());
        RecordingSchedule r = RecordingSchedule.fromJson(o);
        assertEquals(s.id, r.id);
        assertEquals(s.hour, r.hour);
        assertEquals(s.minute, r.minute);
        assertEquals(s.daysMask, r.daysMask);
        assertEquals(s.durationMinutes, r.durationMinutes);
        assertEquals(s.cameraMode, r.cameraMode);
        assertEquals(s.enabled, r.enabled);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadTime() {
        new RecordingSchedule(1, 24, 0, 0, 10, "back", true);
    }
}
