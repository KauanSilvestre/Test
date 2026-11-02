package com.fadcam.kcam.schedule;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import com.fadcam.FLog;
import com.fadcam.R;
import com.fadcam.RecordingStartActivity;
import com.fadcam.RecordingStopActivity;
import com.fadcam.SharedPreferencesManager;

/**
 * Fires scheduled start/stop alarms and restores alarms after reboot or clock changes.
 *
 * <p>Starting the camera from the background is restricted on modern Android. We launch
 * the same {@link RecordingStartActivity} the home-screen shortcuts use; this is allowed
 * when KCam has "Display over other apps". Without it, a notification is posted so one
 * tap starts the recording.
 */
public class ScheduleReceiver extends BroadcastReceiver {

    public static final String ACTION_START = "com.fadcam.kcam.schedule.START";
    public static final String ACTION_STOP = "com.fadcam.kcam.schedule.STOP";
    public static final String EXTRA_ID = "schedule_id";

    private static final String TAG = "KCamScheduleRx";
    private static final String CHANNEL = "kcam_schedule";
    private static final int NOTIF_BASE = 77_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
                RecordingScheduler.rescheduleAll(context);
                return;
            case ACTION_START:
                onStart(context, intent.getIntExtra(EXTRA_ID, -1));
                return;
            case ACTION_STOP:
                onStop(context, intent.getIntExtra(EXTRA_ID, -1));
                return;
            default:
                if ("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
                    RecordingScheduler.rescheduleAll(context);
                }
        }
    }

    private void onStart(Context context, int id) {
        ScheduleStore store = new ScheduleStore(context);
        RecordingSchedule s = store.get(id);
        if (s == null || !s.enabled) return;

        // Arm the next occurrence first so a crash below never breaks the series.
        if (s.isOneShot()) {
            store.put(s.withEnabled(false));
        } else {
            RecordingScheduler.arm(context, s);
        }

        if (SharedPreferencesManager.getInstance(context).isRecordingInProgress()) {
            FLog.i(TAG, "Schedule " + id + " skipped: already recording");
            return;
        }
        store.setActive(id);
        RecordingScheduler.armStop(context, s, System.currentTimeMillis());

        Intent start = new Intent(context, RecordingStartActivity.class)
                .putExtra(RecordingStartActivity.EXTRA_SHORTCUT_CAMERA_MODE, s.cameraMode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        boolean canLaunch = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context);
        if (canLaunch) {
            try {
                context.startActivity(start);
                return;
            } catch (Exception e) {
                FLog.w(TAG, "Direct start failed, falling back to notification", e);
            }
        }
        notifyTapToStart(context, id, start);
    }

    private void onStop(Context context, int id) {
        ScheduleStore store = new ScheduleStore(context);
        if (store.getActive() != id) return; // recording was started by something else
        store.setActive(-1);
        if (!SharedPreferencesManager.getInstance(context).isRecordingInProgress()) return;
        Intent stop = new Intent(context, RecordingStopActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            context.startActivity(stop);
        } catch (Exception e) {
            FLog.w(TAG, "Could not stop scheduled recording", e);
        }
    }

    private void notifyTapToStart(Context context, int id, Intent start) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    context.getString(R.string.kcam_schedule_title), NotificationManager.IMPORTANCE_HIGH));
        }
        PendingIntent pi = PendingIntent.getActivity(context, NOTIF_BASE + id, start,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(context.getString(R.string.kcam_schedule_title))
                .setContentText(context.getString(R.string.kcam_schedule_tap_to_start))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            nm.notify(NOTIF_BASE + id, b.build());
        } catch (SecurityException e) {
            FLog.w(TAG, "Notification permission missing", e);
        }
    }
}
