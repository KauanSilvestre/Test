package com.fadcam.kcam.vision;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.R;
import com.fadcam.kcam.miniapps.DialView;
import com.fadcam.kcam.miniapps.KMiniAppFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/** Audio Vision: sound-activated recording. */
public class AudioVisionFragment extends KMiniAppFragment {

    private static final int[] THRESHOLDS = {45, 50, 55, 60, 65, 70, 75, 80, 85};
    private static final int[] SILENCE = {5, 10, 15, 30, 60, 120};
    private static final int[] CLIPS = {1, 2, 5, 10, 15, 30};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private DialView gauge;
    private TextView level, state, toggleBtn, thresholdBtn, actionBtn, lengthBtn;
    private LinearLayout warnings;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected int titleRes() {
        return R.string.audio_vision_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        warnings = new LinearLayout(requireContext());
        warnings.setOrientation(LinearLayout.VERTICAL);
        body.addView(warnings);
        gauge = new DialView(requireContext());
        gauge.setMode(DialView.Mode.GAUGE);
        addView(body, gauge, 200);
        level = bigValue(body);
        state = text(body, "", 15, COLOR_MUTED, true);
        state.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        toggleBtn = button(body, R.string.kcam_vision_start, v -> toggle());
        thresholdBtn = button(body, R.string.kcam_vision_threshold, v -> pickThreshold());
        actionBtn = button(body, R.string.kcam_vision_action_audio, v -> pickAction());
        lengthBtn = button(body, R.string.kcam_vision_threshold, v -> pickLength());
        LinearLayout info = card(body);
        text(info, getString(R.string.kcam_vision_info), 12, COLOR_MUTED, false);
        render();
    }

    private void toggle() {
        if (AudioVisionService.isRunning()) {
            AudioVisionService.stop(requireContext());
            return;
        }
        String[] perms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.RECORD_AUDIO};
        withPermissions(() -> AudioVisionService.start(requireContext()), perms);
    }

    private void pickThreshold() {
        CharSequence[] l = new CharSequence[THRESHOLDS.length];
        int sel = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            l[i] = THRESHOLDS[i] + " dB";
            if (THRESHOLDS[i] == AudioVisionPrefs.thresholdDb(requireContext())) sel = i;
        }
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.kcam_vision_threshold)
                .setSingleChoiceItems(l, sel, (d, w) -> {
                    AudioVisionPrefs.setThresholdDb(requireContext(), THRESHOLDS[w]);
                    d.dismiss();
                }).show();
    }

    private void pickAction() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.kcam_vision_action)
                .setSingleChoiceItems(new CharSequence[]{
                        getString(R.string.kcam_vision_action_audio),
                        getString(R.string.kcam_vision_action_back),
                        getString(R.string.kcam_vision_action_front)}, AudioVisionPrefs.action(requireContext()), (d, w) -> {
                    AudioVisionPrefs.setAction(requireContext(), w);
                    d.dismiss();
                }).show();
    }

    private void pickLength() {
        boolean audio = AudioVisionPrefs.action(requireContext()) == AudioVisionPrefs.ACTION_AUDIO;
        int[] values = audio ? SILENCE : CLIPS;
        int cur = audio ? AudioVisionPrefs.silenceSec(requireContext()) : AudioVisionPrefs.clipMin(requireContext());
        CharSequence[] l = new CharSequence[values.length];
        int sel = 0;
        for (int i = 0; i < values.length; i++) {
            l[i] = audio ? values[i] + " s" : getString(R.string.kcam_schedule_minutes, values[i]);
            if (values[i] == cur) sel = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(audio ? R.string.kcam_vision_silence : R.string.kcam_vision_clip)
                .setSingleChoiceItems(l, sel, (d, w) -> {
                    if (audio) AudioVisionPrefs.setSilenceSec(requireContext(), values[w]);
                    else AudioVisionPrefs.setClipMin(requireContext(), values[w]);
                    d.dismiss();
                }).show();
    }

    private void render() {
        if (!isAdded()) return;
        boolean on = AudioVisionService.isRunning();
        int db = on ? AudioVisionService.lastLevelDb() : 0;
        int th = AudioVisionPrefs.thresholdDb(requireContext());
        level.setText(on ? String.format(Locale.US, "%d dB", db) : "—");
        gauge.setFraction(db / 120f);
        gauge.setAccentColor(db >= th ? COLOR_ACCENT : COLOR_OK);
        if (!on) state.setText(R.string.kcam_vision_off);
        else if (AudioVisionService.isClipActive()) state.setText(R.string.kcam_vision_recording);
        else state.setText(getString(R.string.kcam_vision_listening_n, AudioVisionService.clipsRecorded()));
        state.setTextColor(on && AudioVisionService.isClipActive() ? COLOR_ACCENT : COLOR_MUTED);
        toggleBtn.setText(on ? R.string.kcam_stop : R.string.kcam_vision_start);
        thresholdBtn.setText(getString(R.string.kcam_vision_threshold_value, th));
        int a = AudioVisionPrefs.action(requireContext());
        actionBtn.setText(a == AudioVisionPrefs.ACTION_BACK ? R.string.kcam_vision_action_back
                : a == AudioVisionPrefs.ACTION_FRONT ? R.string.kcam_vision_action_front : R.string.kcam_vision_action_audio);
        lengthBtn.setText(a == AudioVisionPrefs.ACTION_AUDIO
                ? getString(R.string.kcam_vision_silence_value, AudioVisionPrefs.silenceSec(requireContext()))
                : getString(R.string.kcam_vision_clip_value, AudioVisionPrefs.clipMin(requireContext())));
    }

    private void renderWarnings() {
        warnings.removeAllViews();
        if (AudioVisionPrefs.action(requireContext()) != AudioVisionPrefs.ACTION_AUDIO
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(requireContext())) {
            LinearLayout c = card(warnings);
            text(c, getString(R.string.kcam_vision_need_overlay), 13, COLOR_ACCENT, false);
            button(c, R.string.kcam_grant, v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + requireContext().getPackageName())));
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Override
    protected void onToolResume() {
        renderWarnings();
        handler.post(tick);
    }

    @Override
    protected void onToolPause() {
        handler.removeCallbacks(tick);
    }
}
