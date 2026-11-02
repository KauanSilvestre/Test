package com.fadcam.kcam;

import android.content.Context;
import android.widget.Toast;

import com.fadcam.BuildConfig;
import com.fadcam.R;

/**
 * KCam edition / entitlement gate.
 *
 * <p>The edition is chosen at build time with {@code kcam.edition} in gradle.properties
 * (or {@code -Pkcam.edition=pro} on the command line):
 * <ul>
 *   <li>{@code personal} – the developer's own build, every feature unlocked (default)</li>
 *   <li>{@code free} – public build, only {@link Tier#FREE} features</li>
 *   <li>{@code pro} – public paid build, everything unlocked</li>
 * </ul>
 *
 * <p>GPL note: this is a product/packaging switch, not DRM. Anyone who receives the
 * source can rebuild with another edition, which GPL-3.0 explicitly allows. Durable
 * paid value should come from services (cloud, sync, support), not from hiding code.
 */
public final class Edition {

    public enum Tier { FREE, PRO }

    /** Every gateable KCam feature, with the tier it belongs to. */
    public enum Feature {
        MINI_APPS(Tier.FREE),
        SCHEDULED_RECORDING(Tier.PRO),
        BATCH_EXPORT(Tier.FREE),
        DISGUISE_FLAVORS(Tier.PRO);

        public final Tier tier;

        Feature(Tier tier) {
            this.tier = tier;
        }
    }

    private Edition() {
    }

    public static String current() {
        return BuildConfig.EDITION;
    }

    public static boolean isPersonal() {
        return "personal".equals(BuildConfig.EDITION);
    }

    public static boolean isPro() {
        return "pro".equals(BuildConfig.EDITION) || isPersonal();
    }

    public static boolean has(Feature feature) {
        return feature.tier == Tier.FREE || isPro();
    }

    /**
     * Returns true when the feature is available; otherwise shows a short notice and
     * returns false so callers can simply {@code if (!Edition.require(ctx, F)) return;}.
     */
    public static boolean require(Context context, Feature feature) {
        if (has(feature)) return true;
        if (context != null) {
            Toast.makeText(context, R.string.kcam_feature_locked, Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    public static int labelRes() {
        switch (BuildConfig.EDITION) {
            case "free":
                return R.string.kcam_edition_free;
            case "pro":
                return R.string.kcam_edition_pro;
            default:
                return R.string.kcam_edition_personal;
        }
    }
}
