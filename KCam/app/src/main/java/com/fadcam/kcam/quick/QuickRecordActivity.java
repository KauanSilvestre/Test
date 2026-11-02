package com.fadcam.kcam.quick;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.fadcam.CameraType;
import com.fadcam.FLog;
import com.fadcam.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Quick record" entry point — the target for Samsung's "double press side key → open app",
 * the "quickly open camera" power-button gesture on other phones, the home-screen shortcut
 * and the experimental power-button watcher.
 *
 * <p>If something is already recording, triggering again stops everything (configurable).
 * Otherwise it starts the configured mode, or shows a chooser: back camera, front camera,
 * audio only, screen (with audio) or everything (front + back camera and the screen).
 * Works over the lock screen.
 */
public class QuickRecordActivity extends ComponentActivity {

    public static final String EXTRA_MODE = "quick_mode";
    private static final String TAG = "KCamQuickRecord";

    private QuickMode pending;
    private ActivityResultLauncher<String[]> permLauncher;
    private ActivityResultLauncher<Intent> projectionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();

        permLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                this::onPermissions);
        projectionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                r -> onProjection(r.getResultCode(), r.getData()));

        if (savedInstanceState != null) {
            // recreated while a permission / consent dialog is in flight
            String p = savedInstanceState.getString("pending");
            if (p == null) showChooser();
            else pending = QuickMode.parse(p);
            return;
        }

        if (QuickRecordPrefs.toggleStops(this) && Recorders.isAnythingRecording(this)) {
            Recorders.stopAll(this);
            // No command-receipt buzz here: each service vibrates at its real
            // stop point (Utils.vibrateRecordingStop), so the feedback reflects
            // the actual state, not merely that the stop was requested.
            Toast.makeText(this, R.string.kcam_quick_stopped, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        QuickMode mode = getIntent() != null && getIntent().hasExtra(EXTRA_MODE)
                ? QuickMode.parse(getIntent().getStringExtra(EXTRA_MODE))
                : QuickRecordPrefs.mode(this);
        if (mode == QuickMode.ASK) {
            showChooser();
        } else {
            run(mode);
        }
    }

    @SuppressWarnings("deprecation")
    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (pending != null) out.putString("pending", pending.name());
    }

    // ── Chooser UI ──────────────────────────────────────────────────

    private void showChooser() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> finish());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(24));
        panel.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF161616);
        bg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        panel.setBackground(bg);

        TextView title = new TextView(this);
        title.setText(R.string.kcam_quick_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(12));
        panel.addView(title);

        option(panel, "photo_camera", R.string.kcam_quick_back, QuickMode.BACK);
        option(panel, "camera_front", R.string.kcam_quick_front, QuickMode.FRONT);
        option(panel, "mic", R.string.kcam_quick_audio, QuickMode.AUDIO);
        option(panel, "screen_record", R.string.kcam_quick_screen, QuickMode.SCREEN);
        option(panel, "select_all", R.string.kcam_quick_all, QuickMode.ALL);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(panel, lp);
        setContentView(root);
    }

    private void option(LinearLayout parent, String icon, int label, QuickMode mode) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF242424);
        bg.setCornerRadius(dp(16));
        row.setBackground(bg);

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(24);
        ic.setTextColor(0xFFE43C3C);
        ic.setTypeface(ResourcesCompat.getFont(this, R.font.materialicons));
        row.addView(ic);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setPadding(dp(16), 0, 0, 0);
        row.addView(t);

        row.setOnClickListener(v -> run(mode));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(row, lp);
    }

    // ── Starting ────────────────────────────────────────────────────

    private void run(QuickMode mode) {
        pending = mode;
        List<String> missing = new ArrayList<>();
        for (String p : permissionsFor(mode)) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) {
            permLauncher.launch(missing.toArray(new String[0]));
            return;
        }
        proceed();
    }

    private static List<String> permissionsFor(QuickMode mode) {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.RECORD_AUDIO);
        if (mode == QuickMode.BACK || mode == QuickMode.FRONT || mode == QuickMode.ALL) {
            p.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) p.add(Manifest.permission.POST_NOTIFICATIONS);
        return p;
    }

    private void onPermissions(Map<String, Boolean> result) {
        // Notifications are nice-to-have; camera/mic are required.
        for (Map.Entry<String, Boolean> e : result.entrySet()) {
            boolean required = !Manifest.permission.POST_NOTIFICATIONS.equals(e.getKey());
            if (required && !Boolean.TRUE.equals(e.getValue())) {
                Toast.makeText(this, R.string.kcam_permission_needed, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        proceed();
    }

    private void proceed() {
        QuickMode mode = pending;
        if (mode == null) {
            finish();
            return;
        }
        try {
            switch (mode) {
                case BACK:
                    Recorders.startCamera(this, CameraType.BACK);
                    done(R.string.kcam_quick_started_back);
                    return;
                case FRONT:
                    Recorders.startCamera(this, CameraType.FRONT);
                    done(R.string.kcam_quick_started_front);
                    return;
                case AUDIO:
                    Recorders.startAudio(this);
                    done(R.string.kcam_quick_started_audio);
                    return;
                case SCREEN:
                case ALL:
                    requestProjection();
                    return;
                default:
                    finish();
            }
        } catch (Exception e) {
            FLog.e(TAG, "Quick record failed for " + mode, e);
            com.fadcam.Utils.vibrateRecordingError(this);
            Toast.makeText(this, R.string.kcam_quick_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void requestProjection() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The system screen-capture consent cannot be shown over a secure lock screen.
            km.requestDismissKeyguard(this, null);
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm == null) {
            finish();
            return;
        }
        projectionLauncher.launch(mpm.createScreenCaptureIntent());
    }

    private void onProjection(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, R.string.kcam_quick_screen_denied, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        try {
            if (pending == QuickMode.ALL) {
                // Both cameras keep the microphone; the screen video is silent to avoid two
                // recorders fighting over the mic.
                Recorders.startCamera(this, CameraType.DUAL_PIP);
                Recorders.startScreen(this, resultCode, data, false);
                done(R.string.kcam_quick_started_all);
            } else {
                Recorders.startScreen(this, resultCode, data, true);
                done(R.string.kcam_quick_started_screen);
            }
        } catch (Exception e) {
            FLog.e(TAG, "Screen start failed", e);
            com.fadcam.Utils.vibrateRecordingError(this);
            Toast.makeText(this, R.string.kcam_quick_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void done(int message) {
        // The start confirmation buzz comes from the service once recording
        // actually begins (Utils.vibrateRecordingStart), not from here — so a
        // buzz never implies a start that did not happen.
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
        overridePendingTransitionCompat();
    }

    @SuppressWarnings("deprecation")
    private void overridePendingTransitionCompat() {
        overridePendingTransition(0, 0);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
