package com.fadcam.kcam.quick;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.content.ContextCompat;

import com.fadcam.CameraType;
import com.fadcam.Constants;
import com.fadcam.FLog;
import com.fadcam.SharedPreferencesManager;
import com.fadcam.dualcam.DualCameraCapability;
import com.fadcam.dualcam.service.DualCameraRecordingService;
import com.fadcam.fadrec.MediaProjectionHelper;
import com.fadcam.fadrec.services.ScreenRecordingService;
import com.fadcam.kcam.audio.AudioRecordService;
import com.fadcam.services.RecordingService;
import com.fadcam.utils.ServiceUtils;

/**
 * One place to start/stop every recorder (camera back/front/dual, FadMic audio, FadRec
 * screen). Must be called from a foreground context (a visible activity) because Android
 * only lets camera/microphone services start while the app is in use.
 */
public final class Recorders {

    private static final String TAG = "KCamRecorders";

    private Recorders() {
    }

    public static boolean isCameraRecording(Context c) {
        return SharedPreferencesManager.getInstance(c).isRecordingInProgress();
    }

    public static boolean isScreenRecording(Context c) {
        return SharedPreferencesManager.getInstance(c).isScreenRecordingInProgress()
                && ServiceUtils.isServiceRunning(c, ScreenRecordingService.class);
    }

    public static boolean isAnythingRecording(Context c) {
        return isCameraRecording(c) || AudioRecordService.isRunning() || isScreenRecording(c);
    }

    /** Mirrors RecordingStartActivity: persist the camera choice, then start the service. */
    public static void startCamera(Context c, CameraType type) {
        SharedPreferencesManager sp = SharedPreferencesManager.getInstance(c);
        if (sp.isRecordingInProgress()) return;
        if (type == CameraType.DUAL_PIP && !new DualCameraCapability(c).isSupported()) {
            FLog.w(TAG, "Dual camera unsupported, falling back to back camera");
            type = CameraType.BACK;
        }
        sp.sharedPreferences.edit().putString(Constants.PREF_CAMERA_SELECTION, type.name()).apply();
        Intent i;
        if (type.isDual()) {
            i = new Intent(c, DualCameraRecordingService.class).setAction(Constants.INTENT_ACTION_START_DUAL_RECORDING);
        } else {
            i = new Intent(c, RecordingService.class).setAction(Constants.INTENT_ACTION_START_RECORDING);
        }
        ContextCompat.startForegroundService(c, i);
    }

    public static void startAudio(Context c) {
        if (!AudioRecordService.isRunning()) AudioRecordService.start(c);
    }

    /**
     * Starts FadRec with an already-granted MediaProjection result.
     * @param withAudio record the microphone into the screen video (ensures the source is mic)
     */
    public static void startScreen(Context c, int resultCode, Intent data, boolean withAudio) {
        SharedPreferencesManager sp = SharedPreferencesManager.getInstance(c);
        if (withAudio && Constants.AUDIO_SOURCE_NONE.equals(sp.getScreenRecordingAudioSource())) {
            sp.setScreenRecordingAudioSource(Constants.AUDIO_SOURCE_MIC);
        }
        new MediaProjectionHelper(c).startScreenRecording(resultCode, data, !withAudio);
    }

    /** Stops every recorder that is running. */
    public static void stopAll(Context c) {
        SharedPreferencesManager sp = SharedPreferencesManager.getInstance(c);
        try {
            if (sp.isRecordingInProgress()) {
                boolean dual = ServiceUtils.isServiceRunning(c, DualCameraRecordingService.class)
                        || (sp.getCameraSelection() != null && sp.getCameraSelection().isDual());
                c.startService(dual
                        ? new Intent(c, DualCameraRecordingService.class).setAction(Constants.INTENT_ACTION_STOP_DUAL_RECORDING)
                        : new Intent(c, RecordingService.class).setAction(Constants.INTENT_ACTION_STOP_RECORDING));
            }
        } catch (Exception e) {
            FLog.w(TAG, "Camera stop failed", e);
        }
        AudioRecordService.stop(c);
        try {
            if (isScreenRecording(c)) {
                c.startService(new Intent(c, ScreenRecordingService.class)
                        .setAction(Constants.INTENT_ACTION_STOP_SCREEN_RECORDING));
            }
        } catch (Exception e) {
            FLog.w(TAG, "Screen stop failed", e);
        }
    }

    @SuppressWarnings("deprecation")
    public static void buzz(Context c, int times) {
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        long[] pattern = new long[times * 2];
        for (int i = 0; i < times; i++) {
            pattern[i * 2] = i == 0 ? 0 : 120;
            pattern[i * 2 + 1] = 60;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            v.vibrate(pattern, -1);
        }
    }
}
