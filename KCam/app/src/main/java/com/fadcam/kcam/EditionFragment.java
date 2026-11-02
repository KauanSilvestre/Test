package com.fadcam.kcam;

import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fadcam.BuildConfig;
import com.fadcam.R;
import com.fadcam.kcam.miniapps.KMiniAppFragment;

/** Replaces upstream's "FadCam Pro" sales screen: shows this build's KCam edition. */
public class EditionFragment extends KMiniAppFragment {

    @Override
    protected int titleRes() {
        return R.string.kcam_edition_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        icon(body, Edition.isPro() ? "workspace_premium" : "verified", 64, COLOR_ACCENT);
        TextView name = text(body, BuildConfig.FORK_NAME + " " + getString(Edition.labelRes()), 26, 0xFFFFFFFF, true);
        name.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        TextView sub = text(body, getString(Edition.isPro() ? R.string.kcam_edition_all_unlocked
                : R.string.kcam_edition_free_desc), 14, COLOR_MUTED, false);
        sub.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout c = card(body);
        text(c, getString(R.string.kcam_edition_features_title), 15, 0xFFFFFFFF, true);
        for (Edition.Feature f : Edition.Feature.values()) {
            boolean has = Edition.has(f);
            text(c, (has ? "✓  " : "✕  ") + getString(featureLabel(f)), 14, has ? COLOR_OK : COLOR_MUTED, false);
        }
        LinearLayout about = card(body);
        text(about, getString(R.string.kcam_edition_gpl), 12, COLOR_MUTED, false);
        text(about, "v" + BuildConfig.VERSION_NAME + " · fork " + BuildConfig.FORK_VERSION + " · upstream "
                + BuildConfig.UPSTREAM_COMMIT.substring(0, 7), 11, COLOR_MUTED, false);
    }

    private static int featureLabel(Edition.Feature f) {
        switch (f) {
            case SCHEDULED_RECORDING: return R.string.kcam_schedule_title;
            case BATCH_EXPORT: return R.string.kcam_edition_feature_batch;
            case DISGUISE_FLAVORS: return R.string.kcam_edition_feature_disguise;
            default: return R.string.kcam_edition_feature_miniapps;
        }
    }
}
