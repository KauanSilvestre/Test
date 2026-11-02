package com.fadcam.kcam.quick;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.fadcam.R;
import com.fadcam.kcam.miniapps.KMiniAppFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Settings + setup guide for quick record (double press side key / power button). */
public class QuickRecordSettingsFragment extends KMiniAppFragment {

    private static final QuickMode[] MODES = QuickMode.values();
    private TextView modeBtn;
    private LinearLayout warnings;

    @Override
    protected int titleRes() {
        return R.string.kcam_quick_settings_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        warnings = new LinearLayout(requireContext());
        warnings.setOrientation(LinearLayout.VERTICAL);
        body.addView(warnings);

        modeBtn = button(body, R.string.kcam_quick_mode_ask, v -> pickMode());
        button(body, R.string.kcam_quick_test, v -> startActivity(new Intent(requireContext(), QuickRecordActivity.class)));
        button(body, R.string.kcam_quick_add_shortcut, v -> addShortcut());

        LinearLayout opts = card(body);
        toggle(opts, R.string.kcam_quick_toggle_stops, QuickRecordPrefs.toggleStops(requireContext()),
                on -> QuickRecordPrefs.setToggleStops(requireContext(), on));
        toggle(opts, R.string.kcam_quick_power_watch, QuickRecordPrefs.powerWatch(requireContext()), on -> {
            QuickRecordPrefs.setPowerWatch(requireContext(), on);
            PowerButtonWatchService.sync(requireContext());
            renderWarnings();
        });
        text(opts, getString(R.string.kcam_quick_power_watch_desc), 12, COLOR_MUTED, false);
        android.content.ComponentName alias = new android.content.ComponentName(requireContext(),
                "com.fadcam.kcam.quick.QuickRecordLauncher");
        android.content.pm.PackageManager pm = requireContext().getPackageManager();
        boolean aliasOn = pm.getComponentEnabledSetting(alias)
                != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        toggle(opts, R.string.kcam_quick_show_launcher, aliasOn, on -> pm.setComponentEnabledSetting(alias,
                on ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                        : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP));

        LinearLayout samsung = card(body);
        text(samsung, getString(R.string.kcam_quick_samsung_title), 15, 0xFFFFFFFF, true);
        text(samsung, getString(R.string.kcam_quick_samsung_steps), 13, COLOR_MUTED, false);
        LinearLayout other = card(body);
        text(other, getString(R.string.kcam_quick_other_title), 15, 0xFFFFFFFF, true);
        text(other, getString(R.string.kcam_quick_other_steps), 13, COLOR_MUTED, false);
        button(body, R.string.kcam_quick_open_settings, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        renderMode();
    }

    @Override
    protected void onToolResume() {
        renderWarnings();
    }

    private interface OnToggle {
        void set(boolean on);
    }

    private void toggle(LinearLayout parent, int label, boolean value, OnToggle cb) {
        SwitchCompat sw = new SwitchCompat(requireContext());
        sw.setText(label);
        sw.setTextColor(0xFFFFFFFF);
        sw.setTextSize(14);
        sw.setChecked(value);
        sw.setPadding(0, dp(6), 0, dp(6));
        sw.setOnCheckedChangeListener((b, on) -> cb.set(on));
        parent.addView(sw);
    }

    private void renderWarnings() {
        if (warnings == null || !isAdded()) return;
        warnings.removeAllViews();
        if (QuickRecordPrefs.powerWatch(requireContext())
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && !Settings.canDrawOverlays(requireContext())) {
            LinearLayout c = card(warnings);
            text(c, getString(R.string.kcam_quick_need_overlay), 13, COLOR_ACCENT, false);
            button(c, R.string.kcam_grant, v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + requireContext().getPackageName())));
                } catch (Exception ignored) {
                }
            });
        }
    }

    private int labelFor(QuickMode m) {
        switch (m) {
            case BACK: return R.string.kcam_quick_mode_back;
            case FRONT: return R.string.kcam_quick_mode_front;
            case AUDIO: return R.string.kcam_quick_mode_audio;
            case SCREEN: return R.string.kcam_quick_mode_screen;
            case ALL: return R.string.kcam_quick_mode_all;
            default: return R.string.kcam_quick_mode_ask;
        }
    }

    private void renderMode() {
        modeBtn.setText(labelFor(QuickRecordPrefs.mode(requireContext())));
    }

    private void pickMode() {
        CharSequence[] labels = new CharSequence[MODES.length];
        for (int i = 0; i < MODES.length; i++) labels[i] = getString(labelFor(MODES[i]));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kcam_quick_mode_title)
                .setSingleChoiceItems(labels, QuickRecordPrefs.mode(requireContext()).ordinal(), (d, which) -> {
                    QuickRecordPrefs.setMode(requireContext(), MODES[which]);
                    renderMode();
                    d.dismiss();
                })
                .show();
    }

    private void addShortcut() {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
            Toast.makeText(requireContext(), R.string.kcam_no_app, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(requireContext(), QuickRecordActivity.class).setAction(Intent.ACTION_VIEW);
        ShortcutInfoCompat sc = new ShortcutInfoCompat.Builder(requireContext(), "kcam_quick_record")
                .setShortLabel(getString(R.string.kcam_quick_launcher_label))
                .setIcon(IconCompat.createWithResource(requireContext(), R.mipmap.ic_launcher))
                .setIntent(i)
                .build();
        ShortcutManagerCompat.requestPinShortcut(requireContext(), sc, null);
    }
}
