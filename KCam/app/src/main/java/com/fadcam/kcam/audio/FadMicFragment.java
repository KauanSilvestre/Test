package com.fadcam.kcam.audio;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.fadcam.R;
import com.fadcam.kcam.miniapps.KMiniAppFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/** FadMic screen: start/stop audio-only recording, quality, and a list of recordings. */
public class FadMicFragment extends KMiniAppFragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView timer, status, recordBtn, qualityBtn;
    private LinearLayout list;
    private MediaPlayer player;
    private String playingPath;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderState();
            handler.postDelayed(this, 500);
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderState();
            renderList();
        }
    };

    @Override
    protected int titleRes() {
        return R.string.kcam_fadmic_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        icon(body, "mic", 72, COLOR_ACCENT);
        timer = bigValue(body);
        status = text(body, "", 15, COLOR_MUTED, true);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        recordBtn = button(body, R.string.kcam_fadmic_start, v -> toggle());
        qualityBtn = button(body, R.string.kcam_fadmic_quality_standard, v -> pickQuality());
        LinearLayout info = card(body);
        text(info, getString(R.string.kcam_fadmic_info), 12, COLOR_MUTED, false);
        text(body, getString(R.string.kcam_fadmic_recordings), 16, 0xFFFFFFFF, true).setPadding(0, dp(12), 0, dp(8));
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
    }

    @Override
    protected void onToolResume() {
        ContextCompat.registerReceiver(requireContext(), stateReceiver,
                new IntentFilter(AudioRecordService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED);
        handler.post(tick);
        renderList();
    }

    @Override
    protected void onToolPause() {
        handler.removeCallbacks(tick);
        try {
            requireContext().unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {
        }
        stopPlayback();
    }

    private void toggle() {
        if (AudioRecordService.isRunning()) {
            AudioRecordService.stop(requireContext());
            return;
        }
        String[] perms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.RECORD_AUDIO};
        withPermissions(() -> {
            stopPlayback();
            AudioRecordService.start(requireContext());
            handler.postDelayed(this::renderState, 400);
        }, perms);
    }

    private void renderState() {
        if (!isAdded()) return;
        boolean on = AudioRecordService.isRunning();
        long s = AudioRecordService.elapsedMs() / 1000;
        timer.setText(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        status.setText(on ? R.string.kcam_fadmic_recording : R.string.kcam_fadmic_idle);
        status.setTextColor(on ? COLOR_ACCENT : COLOR_MUTED);
        recordBtn.setText(on ? R.string.kcam_stop : R.string.kcam_fadmic_start);
        int q = AudioPrefs.quality(requireContext());
        qualityBtn.setText(q == 0 ? R.string.kcam_fadmic_quality_voice
                : q == 2 ? R.string.kcam_fadmic_quality_high : R.string.kcam_fadmic_quality_standard);
        qualityBtn.setEnabled(!on);
        qualityBtn.setAlpha(on ? 0.4f : 1f);
    }

    private void pickQuality() {
        if (AudioRecordService.isRunning()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kcam_fadmic_quality)
                .setSingleChoiceItems(new CharSequence[]{
                        getString(R.string.kcam_fadmic_quality_voice),
                        getString(R.string.kcam_fadmic_quality_standard),
                        getString(R.string.kcam_fadmic_quality_high)}, AudioPrefs.quality(requireContext()),
                        (d, which) -> {
                            AudioPrefs.setQuality(requireContext(), which);
                            d.dismiss();
                            renderState();
                        })
                .show();
    }

    private void renderList() {
        if (!isAdded()) return;
        list.removeAllViews();
        File[] files = AudioRecordService.audioDir(requireContext()).listFiles((d, n) -> n.endsWith(".m4a"));
        if (files == null || files.length == 0) {
            text(list, getString(R.string.kcam_fadmic_empty), 13, COLOR_MUTED, false);
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String active = AudioRecordService.isRunning() ? AudioRecordService.currentPath() : null;
        for (File f : files) {
            if (f.getAbsolutePath().equals(active)) continue; // still being written
            LinearLayout c = card(list);
            text(c, f.getName(), 14, 0xFFFFFFFF, true);
            text(c, DateUtils.getRelativeTimeSpanString(f.lastModified()) + " · "
                    + Formatter.formatShortFileSize(requireContext(), f.length()), 12, COLOR_MUTED, false);
            boolean playing = f.getAbsolutePath().equals(playingPath);
            TextView hint = text(c, getString(playing ? R.string.kcam_fadmic_tap_stop : R.string.kcam_fadmic_tap_play),
                    12, playing ? COLOR_OK : COLOR_MUTED, false);
            hint.setPadding(0, dp(4), 0, 0);
            c.setOnClickListener(v -> togglePlay(f));
            c.setOnLongClickListener(v -> {
                actions(f);
                return true;
            });
        }
    }

    private void togglePlay(File f) {
        if (f.getAbsolutePath().equals(playingPath)) {
            stopPlayback();
            renderList();
            return;
        }
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                stopPlayback();
                renderList();
            });
            player.prepare();
            player.start();
            playingPath = f.getAbsolutePath();
        } catch (Exception e) {
            stopPlayback();
            Toast.makeText(requireContext(), R.string.kcam_fadmic_play_error, Toast.LENGTH_SHORT).show();
        }
        renderList();
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        playingPath = null;
    }

    private void actions(File f) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(f.getName())
                .setItems(new CharSequence[]{getString(R.string.kcam_share), getString(R.string.kcam_delete)},
                        (d, which) -> {
                            if (which == 0) share(f);
                            else confirmDelete(f);
                        })
                .show();
    }

    private void share(File f) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", f);
            Intent send = new Intent(Intent.ACTION_SEND).setType("audio/mp4")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.kcam_share)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.kcam_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(File f) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kcam_delete_confirm)
                .setMessage(f.getName())
                .setPositiveButton(R.string.kcam_delete, (d, w) -> {
                    if (f.getAbsolutePath().equals(playingPath)) stopPlayback();
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    renderList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
