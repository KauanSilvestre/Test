package com.fadcam.kcam.schedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.fadcam.FLog;

import java.util.TimeZone;

/** Arms/disarms AlarmManager alarms for {@link RecordingSchedule}s. */
public final class RecordingScheduler {

    private static final String TAG = "KCamScheduler";
    /** Request-code offset so stop alarms never collide with start alarms. */
    private static final int STOP_OFFSET = 100_000;

    private RecordingScheduler() {
    }

    /** Re-arms every enabled schedule (call after edits, boot, time/zone changes). */
    public static void rescheduleAll(Context context) {
        ScheduleStore store = new ScheduleStore(context);
        for (RecordingSchedule s : store.all()) {
            if (s.enabled) arm(context, s);
            else cancel(context, s.id);
        }
    }

    public static long arm(Context context, RecordingSchedule s) {
        long at = s.nextStartAfter(System.currentTimeMillis(), TimeZone.getDefault());
        if (at < 0) return -1;
        setAlarm(context, at, pending(context, ScheduleReceiver.ACTION_START, s.id, s.id));
        FLog.d(TAG, "Armed schedule " + s.id + " at " + at);
        return at;
    }

    public static void armStop(Context context, RecordingSchedule s, long startedAt) {
        long at = startedAt + s.durationMinutes * 60_000L;
        setAlarm(context, at, pending(context, ScheduleReceiver.ACTION_STOP, s.id, STOP_OFFSET + s.id));
    }

    public static void cancel(Context context, int id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pending(context, ScheduleReceiver.ACTION_START, id, id));
        am.cancel(pending(context, ScheduleReceiver.ACTION_STOP, id, STOP_OFFSET + id));
    }

    /** Whether exact timing is allowed (Android 12+ needs the "Alarms & reminders" grant). */
    public static boolean canUseExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private static void setAlarm(Context context, long at, PendingIntent pi) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            if (canUseExact(context)) {
                // setAlarmClock is exact even in Doze and grants a background-start exemption
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, null), pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static PendingIntent pending(Context context, String action, int scheduleId, int requestCode) {
        Intent i = new Intent(context, ScheduleReceiver.class)
                .setAction(action)
                .putExtra(ScheduleReceiver.EXTRA_ID, scheduleId);
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
