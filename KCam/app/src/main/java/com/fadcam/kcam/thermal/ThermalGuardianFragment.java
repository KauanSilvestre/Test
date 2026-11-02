package com.fadcam.kcam.thermal;

import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import com.fadcam.R;
import com.fadcam.kcam.miniapps.KMiniAppFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/** Thermal Guardian status + settings. */
public class ThermalGuardianFragment extends KMiniAppFragment {

    private static final int[] TEMPS = {38, 40, 42, 43, 44, 45, 46, 47, 48, 50, 52};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView temp, statusTv, warnBtn, stopBtn;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected int titleRes() {
        return R.string.thermal_guardian_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        ThermalGuardian.install(requireContext());
        icon(body, "device_thermostat", 64, COLOR_ACCENT);
        temp = bigValue(body);
        statusTv = text(body, "", 15, COLOR_MUTED, true);
        statusTv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout c = card(body);
        sw(c, R.string.kcam_thermal_enabled, ThermalGuardian.enabled(requireContext()),
                on -> ThermalGuardian.setEnabled(requireContext(), on));
        sw(c, R.string.kcam_thermal_autostop, ThermalGuardian.autoStop(requireContext()),
                on -> ThermalGuardian.setAutoStop(requireContext(), on));
        warnBtn = button(body, R.string.kcam_thermal_warn_at, v -> pick(true));
        stopBtn = button(body, R.string.kcam_thermal_stop_at, v -> pick(false));
        LinearLayout info = card(body);
        text(info, getString(R.string.kcam_thermal_info), 12, COLOR_MUTED, false);
        render();
    }

    private interface Cb { void set(boolean on); }

    private void sw(LinearLayout parent, int label, boolean value, Cb cb) {
        SwitchCompat s = new SwitchCompat(requireContext());
        s.setText(label);
        s.setTextColor(0xFFFFFFFF);
        s.setChecked(value);
        s.setPadding(0, dp(6), 0, dp(6));
        s.setOnCheckedChangeListener((b, on) -> cb.set(on));
        parent.addView(s);
    }

    private void pick(boolean warn) {
        CharSequence[] labels = new CharSequence[TEMPS.length];
        int sel = 0;
        int cur = warn ? ThermalGuardian.warnTemp(requireContext()) : ThermalGuardian.stopTemp(requireContext());
        for (int i = 0; i < TEMPS.length; i++) {
            labels[i] = TEMPS[i] + " °C";
            if (TEMPS[i] == cur) sel = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(warn ? R.string.kcam_thermal_warn_title : R.string.kcam_thermal_stop_title)
                .setSingleChoiceItems(labels, sel, (d, which) -> {
                    if (warn) ThermalGuardian.setWarnTemp(requireContext(), TEMPS[which]);
                    else ThermalGuardian.setStopTemp(requireContext(), TEMPS[which]);
                    d.dismiss();
                    render();
                })
                .show();
    }

    private void render() {
        if (!isAdded()) return;
        float t = ThermalGuardian.lastTemperatureC();
        temp.setText(Float.isNaN(t) ? "—" : String.format(Locale.US, "%.1f °C", t));
        int st = ThermalGuardian.lastThermalStatus();
        int res;
        if (st >= 4) res = R.string.kcam_thermal_status_critical;
        else if (st == 3) res = R.string.kcam_thermal_status_severe;
        else if (st >= 1) res = R.string.kcam_thermal_status_warm;
        else res = R.string.kcam_thermal_status_ok;
        statusTv.setText(res);
        statusTv.setTextColor(st >= 3 ? COLOR_ACCENT : COLOR_OK);
        warnBtn.setText(getString(R.string.kcam_thermal_warn_value, ThermalGuardian.warnTemp(requireContext())));
        stopBtn.setText(getString(R.string.kcam_thermal_stop_value, ThermalGuardian.stopTemp(requireContext())));
    }

    @Override
    protected void onToolResume() {
        handler.post(tick);
    }

    @Override
    protected void onToolPause() {
        handler.removeCallbacks(tick);
    }
}
