package com.fadcam.kcam.schedule;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import com.fadcam.R;
import com.fadcam.RecordingStartActivity;
import com.fadcam.kcam.Edition;
import com.fadcam.kcam.miniapps.KMiniAppFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** List / add / toggle / delete scheduled recordings. */
public class ScheduledRecordingFragment extends KMiniAppFragment {

    private static final int[] DURATIONS = {1, 5, 10, 15, 30, 60, 120, 240, 480};
    private static final String[] CAMERA_MODES = {
            RecordingStartActivity.CAMERA_MODE_CURRENT, RecordingStartActivity.CAMERA_MODE_BACK,
            RecordingStartActivity.CAMERA_MODE_FRONT, RecordingStartActivity.CAMERA_MODE_DUAL};

    private ScheduleStore store;
    private LinearLayout list;
    private LinearLayout warnings;

    @Override
    protected int titleRes() {
        return R.string.kcam_schedule_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        store = new ScheduleStore(requireContext());
        warnings = new LinearLayout(requireContext());
        warnings.setOrientation(LinearLayout.VERTICAL);
        body.addView(warnings);
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        button(body, R.string.kcam_schedule_add, v -> {
            if (Edition.require(requireContext(), Edition.Feature.SCHEDULED_RECORDING)) startAddFlow();
        });
        LinearLayout info = card(body);
        text(info, getString(R.string.kcam_schedule_info), 12, COLOR_MUTED, false);
    }

    @Override
    protected void onToolResume() {
        renderWarnings();
        renderList();
    }

    private void renderWarnings() {
        warnings.removeAllViews();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(requireContext())) {
            LinearLayout c = card(warnings);
            text(c, getString(R.string.kcam_schedule_need_overlay), 13, COLOR_ACCENT, false);
            button(c, R.string.kcam_grant, v -> openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
        if (!RecordingScheduler.canUseExact(requireContext()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LinearLayout c = card(warnings);
            text(c, getString(R.string.kcam_schedule_need_exact), 13, COLOR_ACCENT, false);
            button(c, R.string.kcam_grant, v -> openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action, Uri.parse("package:" + requireContext().getPackageName())));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.kcam_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderList() {
        list.removeAllViews();
        List<RecordingSchedule> all = store.all();
        if (all.isEmpty()) {
            TextView t = text(list, getString(R.string.kcam_schedule_empty), 14, COLOR_MUTED, false);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setPadding(0, dp(24), 0, dp(24));
            return;
        }
        for (RecordingSchedule s : all) {
            LinearLayout c = card(list);
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            c.addView(row);
            LinearLayout texts = new LinearLayout(requireContext());
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            text(texts, formatTime(s.hour, s.minute), 26, 0xFFFFFFFF, true);
            text(texts, describe(s), 12, COLOR_MUTED, false);
            if (s.enabled) {
                long next = s.nextStartAfter(System.currentTimeMillis(), TimeZone.getDefault());
                if (next > 0) {
                    text(texts, getString(R.string.kcam_schedule_next,
                            DateFormat.getMediumDateFormat(requireContext()).format(new Date(next)) + " "
                                    + formatTime(s.hour, s.minute)), 12, COLOR_OK, false);
                }
            }
            SwitchCompat sw = new SwitchCompat(requireContext());
            sw.setChecked(s.enabled);
            sw.setOnCheckedChangeListener((b, on) -> {
                RecordingSchedule updated = s.withEnabled(on);
                store.put(updated);
                if (on) RecordingScheduler.arm(requireContext(), updated);
                else RecordingScheduler.cancel(requireContext(), s.id);
                renderList();
            });
            row.addView(sw);
            c.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.kcam_schedule_delete)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            RecordingScheduler.cancel(requireContext(), s.id);
                            store.remove(s.id);
                            renderList();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }
        TextView hint = text(list, getString(R.string.kcam_schedule_long_press), 11, COLOR_MUTED, false);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
    }

    private String formatTime(int h, int m) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        return DateFormat.getTimeFormat(requireContext()).format(c.getTime());
    }

    private String describe(RecordingSchedule s) {
        String days;
        if (s.daysMask == 0) days = getString(R.string.kcam_schedule_once);
        else if (s.daysMask == RecordingSchedule.EVERY_DAY) days = getString(R.string.kcam_schedule_every_day);
        else {
            String[] names = weekdayNames();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if ((s.daysMask & (1 << i)) != 0) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(names[i]);
                }
            }
            days = sb.toString();
        }
        return days + " · " + getString(R.string.kcam_schedule_minutes, s.durationMinutes)
                + " · " + cameraLabel(s.cameraMode);
    }

    /** Short weekday names, Monday first. */
    private static String[] weekdayNames() {
        String[] w = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays(); // index 1 = Sunday
        return new String[]{w[2], w[3], w[4], w[5], w[6], w[7], w[1]};
    }

    private String cameraLabel(String mode) {
        String[] labels = getResources().getStringArray(R.array.kcam_schedule_cameras);
        for (int i = 0; i < CAMERA_MODES.length; i++) if (CAMERA_MODES[i].equals(mode)) return labels[i];
        return mode;
    }

    // ── Add flow: time → days → duration → camera ───────────────────

    private void startAddFlow() {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(requireContext(), (tp, h, m) -> pickDays(h, m),
                now.get(Calendar.HOUR_OF_DAY), (now.get(Calendar.MINUTE) + 5) % 60,
                DateFormat.is24HourFormat(requireContext())).show();
    }

    private void pickDays(int h, int m) {
        String[] names = weekdayNames();
        boolean[] checked = new boolean[7];
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kcam_schedule_days)
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int mask = 0;
                    for (int i = 0; i < 7; i++) if (checked[i]) mask |= 1 << i;
                    pickDuration(h, m, mask);
                })
                .setNeutralButton(R.string.kcam_schedule_once, (d, w) -> pickDuration(h, m, 0))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickDuration(int h, int m, int mask) {
        String[] labels = new String[DURATIONS.length];
        for (int i = 0; i < DURATIONS.length; i++) labels[i] = getString(R.string.kcam_schedule_minutes, DURATIONS[i]);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kcam_schedule_duration)
                .setItems(labels, (d, which) -> pickCamera(h, m, mask, DURATIONS[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickCamera(int h, int m, int mask, int minutes) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kcam_schedule_camera)
                .setItems(R.array.kcam_schedule_cameras, (d, which) -> {
                    RecordingSchedule s = new RecordingSchedule(store.newId(), h, m, mask, minutes,
                            CAMERA_MODES[which], true);
                    store.put(s);
                    RecordingScheduler.arm(requireContext(), s);
                    renderList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
