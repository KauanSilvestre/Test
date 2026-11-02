package com.fadcam.kcam.quick;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.fadcam.FLog;
import com.fadcam.R;

/**
 * Experimental "double press power" trigger for phones without a configurable side key.
 *
 * <p>Apps cannot read the power key. What they can see is the screen turning off and on:
 * pressing power twice quickly produces two screen-state changes within a few hundred
 * milliseconds, which a normal tap never does. When that happens we open
 * {@link QuickRecordActivity}. Requires "Display over other apps" so the activity may start
 * from the background, and keeps a small persistent notification (Android requirement).
 */
public class PowerButtonWatchService extends Service {

    private static final String TAG = "KCamPowerWatch";
    private static final String CHANNEL = "kcam_power_watch";
    private static final int NOTIF_ID = 77_600;
    /** Max gap between the two screen toggles of a double press. */
    private static final long WINDOW_MS = 700;
    /** Ignore new triggers for a moment after one fires. */
    private static final long COOLDOWN_MS = 2500;

    private long lastToggle;
    private long lastFire;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastToggle <= WINDOW_MS && now - lastFire > COOLDOWN_MS) {
                lastFire = now;
                lastToggle = 0;
                fire();
            } else {
                lastToggle = now;
            }
        }
    };

    public static void sync(Context c) {
        Intent i = new Intent(c, PowerButtonWatchService.class);
        if (QuickRecordPrefs.powerWatch(c)) {
            try {
                ContextCompat.startForegroundService(c, i);
            } catch (Exception e) {
                FLog.w(TAG, "Could not start power watcher", e);
            }
        } else {
            c.stopService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        // System broadcasts: registering not-exported is fine, they are still delivered.
        ContextCompat.registerReceiver(this, screen, f, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!QuickRecordPrefs.powerWatch(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
        } catch (Exception e) {
            FLog.w(TAG, "startForeground failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void fire() {
        FLog.i(TAG, "Double power press detected");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(this)) {
            FLog.w(TAG, "No overlay permission: cannot open quick record from background");
            return;
        }
        try {
            startActivity(new Intent(this, QuickRecordActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
        } catch (Exception e) {
            FLog.w(TAG, "Could not open quick record", e);
        }
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.kcam_quick_power_channel),
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 3,
                new Intent(this, QuickRecordActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(getString(R.string.kcam_quick_power_title))
                .setContentText(getString(R.string.kcam_quick_power_text))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setContentIntent(open)
                .build();
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screen);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
