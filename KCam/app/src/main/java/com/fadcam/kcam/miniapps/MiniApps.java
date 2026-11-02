package com.fadcam.kcam.miniapps;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fadcam.FLog;
import com.fadcam.R;
import com.fadcam.kcam.Edition;
import com.fadcam.ui.OverlayNavUtil;

/**
 * Router for KCam's implementations of the mini apps upstream ships as "coming soon".
 * Upstream call sites keep calling {@code HomeSidebarFragment.showMiniAppComingSoon};
 * that method asks {@link #open} first, so the hook is a single line.
 */
public final class MiniApps {

    private static final String TAG = "KCamMiniApps";

    /** Upstream mini-app ids that KCam implements. */
    public static final String[] IMPLEMENTED = {
            "compass", "sound_meter", "sensor_dashboard", "speedometer", "clinometer",
            "pedometer", "metal_detector", "parking_marker", "qr_generator"
    };

    private MiniApps() {
    }

    public static boolean isImplemented(String appId) {
        for (String id : IMPLEMENTED) if (id.equals(appId)) return true;
        return false;
    }

    private static Fragment create(String appId) {
        switch (appId) {
            case "compass": return new CompassFragment();
            case "sound_meter": return new SoundMeterFragment();
            case "sensor_dashboard": return new SensorDashboardFragment();
            case "speedometer": return new SpeedometerFragment();
            case "clinometer": return new ClinometerFragment();
            case "pedometer": return new PedometerFragment();
            case "metal_detector": return new MetalDetectorFragment();
            case "parking_marker": return new ParkingMarkerFragment();
            case "qr_generator": return new QrGeneratorFragment();
            default: return null;
        }
    }

    /**
     * Opens the mini app if KCam implements it.
     * @return true when handled (caller must not show the "coming soon" sheet)
     */
    public static boolean open(Fragment from, String appId) {
        if (from == null || appId == null) return false;
        Fragment f = create(appId);
        if (f == null) return false;
        FragmentActivity activity = from.getActivity();
        if (activity == null) return true;
        if (!Edition.require(activity, Edition.Feature.MINI_APPS)) return true;
        try {
            if (from instanceof DialogFragment && ((DialogFragment) from).getDialog() != null) {
                ((DialogFragment) from).dismiss(); // e.g. the home sidebar
            }
            OverlayNavUtil.show(activity, f, "kcam_mini_" + appId);
        } catch (Exception e) {
            FLog.w(TAG, "Failed to open mini app " + appId, e);
        }
        return true;
    }

    /**
     * Hides every "Soon" badge inside {@code root} (upstream layouts hard-code them on
     * the rows of mini apps that KCam now implements).
     */
    public static void hideComingSoonBadges(View root) {
        if (root == null) return;
        android.content.Context c = root.getContext();
        String[] soon = {
                c.getString(R.string.mini_app_coming_soon),
                c.getString(R.string.remote_coming_soon_badge),
                c.getString(R.string.digital_forensics_coming_soon_short)};
        hide(root, soon);
    }

    private static void hide(View v, String[] soon) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null) {
                for (String s : soon) {
                    if (s.contentEquals(t)) {
                        v.setVisibility(View.GONE);
                        break;
                    }
                }
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) hide(g.getChildAt(i), soon);
        }
    }
}
