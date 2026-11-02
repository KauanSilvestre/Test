package com.fadcam.kcam.home;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.fadcam.Constants;
import com.fadcam.MainActivity;
import com.fadcam.R;
import com.fadcam.SharedPreferencesManager;
import com.fadcam.kcam.quick.QuickRecordActivity;

/**
 * Sentinela — minimal launch screen for the personal-safety recorder.
 *
 * <p>Deliberately tiny and built in code (no heavy layout): one evident flow —
 * <b>acionar → encerrar</b> with the big button (delegates to
 * {@link QuickRecordActivity}, which owns all the start/stop, permission and
 * screen-capture-consent logic), plus <b>configurar</b> (capture settings) and
 * <b>acessar</b> (the full app with the recordings library).
 *
 * <p>First run is forwarded to {@link MainActivity} so the existing onboarding
 * and permission priming still happen; after that, cold launches land here.
 * Everything is wrapped defensively and a "full app" button is always visible,
 * so the user can always reach the complete app even if something here is off.
 */
public class SentinelaHomeActivity extends ComponentActivity {

    private TextView status;
    private TextView bigButtonLabel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshState();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // First run: let the full app run onboarding / permission priming.
        if (!onboardingCompleted()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(buildUi());
    }

    private boolean onboardingCompleted() {
        try {
            return SharedPreferencesManager.getInstance(this).sharedPreferences
                    .getBoolean(Constants.COMPLETED_ONBOARDING_KEY, false);
        } catch (Exception e) {
            // If we cannot tell, assume completed so we never trap the user in a loop.
            return true;
        }
    }

    // ── UI (built in code; explicit colors so it never depends on theme attrs) ──

    private View buildUi() {
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(0xFF0E0E0E);
        scroller.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(32));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView tagline = new TextView(this);
        tagline.setText(R.string.sentinela_home_tagline);
        tagline.setTextColor(0xFFB8B8B8);
        tagline.setTextSize(14);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, dp(6), 0, dp(28));
        root.addView(tagline);

        // Big primary action: start/stop (delegates to QuickRecordActivity).
        LinearLayout big = new LinearLayout(this);
        big.setOrientation(LinearLayout.VERTICAL);
        big.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFE43C3C);
        bg.setCornerRadius(dp(28));
        big.setBackground(bg);
        big.setClickable(true);
        big.setFocusable(true);
        int size = dp(180);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(size, size);
        blp.topMargin = dp(8);
        big.setOnClickListener(v -> openQuickRecord());

        bigButtonLabel = new TextView(this);
        bigButtonLabel.setTextColor(Color.WHITE);
        bigButtonLabel.setTextSize(22);
        bigButtonLabel.setTypeface(Typeface.DEFAULT_BOLD);
        bigButtonLabel.setGravity(Gravity.CENTER);
        big.addView(bigButtonLabel);
        root.addView(big, blp);

        status = new TextView(this);
        status.setTextColor(0xFFB8B8B8);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(18), 0, dp(28));
        root.addView(status);

        secondary(root, R.string.sentinela_home_records, v -> openFullApp());
        secondary(root, R.string.sentinela_home_configure, v -> openFullApp());

        TextView note = new TextView(this);
        note.setText(R.string.sentinela_home_note);
        note.setTextColor(0xFF7A7A7A);
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(8), dp(24), dp(8), 0);
        root.addView(note);

        scroller.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    private void secondary(LinearLayout parent, int label, View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E1E1E);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        parent.addView(b, lp);
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    private void openQuickRecord() {
        try {
            // QuickRecordActivity toggles: starts the configured capture when idle,
            // stops everything when already recording.
            startActivity(new Intent(this, QuickRecordActivity.class));
        } catch (Exception ignored) {
        }
    }

    private void openFullApp() {
        try {
            startActivity(new Intent(this, MainActivity.class));
        } catch (Exception ignored) {
        }
    }

    // ── State ───────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) {
            refreshState();
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, 1000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void refreshState() {
        if (status == null || bigButtonLabel == null) {
            return;
        }
        boolean recording;
        try {
            recording = com.fadcam.kcam.quick.Recorders.isAnythingRecording(this);
        } catch (Exception e) {
            recording = false;
        }
        if (recording) {
            bigButtonLabel.setText(R.string.sentinela_home_stop);
            status.setText(R.string.sentinela_home_state_recording);
            status.setTextColor(0xFFE43C3C);
        } else {
            bigButtonLabel.setText(R.string.sentinela_home_record);
            status.setText(R.string.sentinela_home_state_idle);
            status.setTextColor(0xFFB8B8B8);
        }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
