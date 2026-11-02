package com.fadcam.kcam.miniapps;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.fadcam.R;
import com.fadcam.ui.BaseFragment;
import com.fadcam.ui.OverlayNavUtil;

import java.util.Map;

/**
 * Base class for KCam mini apps. Builds a header (close button + title) and a scrollable
 * body in code so every tool shares the same look without per-tool layout XML.
 * Subclasses implement {@link #buildBody(LinearLayout)} and the lifecycle hooks
 * {@link #onToolResume()} / {@link #onToolPause()} for sensors.
 */
public abstract class KMiniAppFragment extends BaseFragment {

    protected static final int COLOR_CARD = 0xFF1E1E1E;
    protected static final int COLOR_MUTED = 0xFF9E9E9E;
    protected static final int COLOR_ACCENT = 0xFFE43C3C;
    protected static final int COLOR_OK = 0xFF4CAF50;

    private ActivityResultLauncher<String[]> permissionLauncher;
    private Runnable afterPermissionGranted;
    private boolean permissionRequestInFlight;
    private boolean permissionDenied;
    private boolean resumed;

    /** Title string resource shown in the header. */
    protected abstract int titleRes();

    /** Add the tool UI into {@code body}. Called once per view creation. */
    protected abstract void buildBody(@NonNull LinearLayout body);

    /** Start sensors / listeners. Called when visible. */
    protected void onToolResume() {
    }

    /** Stop sensors / listeners. Always paired with {@link #onToolResume()}. */
    protected void onToolPause() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = inflater.getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(resolveColor(ctx, android.R.attr.colorBackground, Color.BLACK));
        root.setClickable(true); // swallow touches so the screen below is not reachable

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(12));
        header.setBackgroundResource(R.drawable.header_bar_rounded_bg);

        ImageView close = new ImageView(ctx);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(Color.WHITE);
        close.setContentDescription(getString(R.string.button_close));
        close.setPadding(dp(2), dp(2), dp(2), dp(2));
        close.setOnClickListener(v -> close());
        header.addView(close, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView title = new TextView(ctx);
        title.setText(titleRes());
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(dp(16));
        header.addView(title, tlp);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildBody(body);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!resumed) {
            resumed = true;
            onToolResume();
        }
    }

    @Override
    public void onPause() {
        if (resumed) {
            resumed = false;
            onToolPause();
        }
        super.onPause();
    }

    protected void close() {
        if (getActivity() != null) OverlayNavUtil.dismiss(requireActivity());
    }

    // ── Permissions ─────────────────────────────────────────────────

    protected boolean hasPermissions(String... permissions) {
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Runs {@code then} immediately if granted, otherwise after the user grants all. */
    protected void withPermissions(Runnable then, String... permissions) {
        if (hasPermissions(permissions)) {
            then.run();
            return;
        }
        // Never re-prompt automatically after a denial (onResume would loop), and never
        // stack two system dialogs.
        if (permissionDenied || permissionRequestInFlight) return;
        afterPermissionGranted = then;
        permissionRequestInFlight = true;
        permissionLauncher.launch(permissions);
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        permissionRequestInFlight = false;
        boolean all = !result.isEmpty();
        for (Boolean granted : result.values()) all &= Boolean.TRUE.equals(granted);
        Runnable r = afterPermissionGranted;
        afterPermissionGranted = null;
        if (all && r != null && isAdded()) {
            r.run();
        } else if (!all && isAdded()) {
            permissionDenied = true;
            onPermissionDenied();
        }
    }

    protected void onPermissionDenied() {
        android.widget.Toast.makeText(requireContext(), R.string.kcam_permission_needed,
                android.widget.Toast.LENGTH_LONG).show();
    }

    // ── UI helpers ──────────────────────────────────────────────────

    protected int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    protected static int resolveColor(Context ctx, int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) return ContextCompat.getColor(ctx, tv.resourceId);
            return tv.data;
        }
        return fallback;
    }

    /** A rounded dark card container. */
    protected LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(requireContext());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    protected TextView text(LinearLayout parent, CharSequence s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        parent.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    protected TextView label(LinearLayout parent, int res) {
        return text(parent, getString(res), 12, COLOR_MUTED, false);
    }

    /** Huge centered numeric readout. */
    protected TextView bigValue(LinearLayout parent) {
        TextView t = text(parent, "—", 56, Color.WHITE, true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return t;
    }

    /** Material Icons ligature glyph (font bundled by upstream). */
    protected TextView icon(LinearLayout parent, String ligature, float sizeSp, int color) {
        TextView t = text(parent, ligature, sizeSp, color, false);
        t.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.materialicons));
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    protected TextView button(LinearLayout parent, int textRes, View.OnClickListener l) {
        TextView b = new TextView(requireContext());
        b.setText(textRes);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_ACCENT);
        bg.setCornerRadius(dp(24));
        b.setBackground(bg);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    /** Two-column "label: value" row; returns the value TextView. */
    protected TextView statRow(LinearLayout parent, int labelRes) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView l = new TextView(requireContext());
        l.setText(labelRes);
        l.setTextColor(COLOR_MUTED);
        l.setTextSize(14);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(requireContext());
        v.setText("—");
        v.setTextColor(Color.WHITE);
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(row);
        return v;
    }

    protected void addView(LinearLayout parent, View v, int heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                heightDp > 0 ? dp(heightDp) : ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(v, lp);
    }
}
