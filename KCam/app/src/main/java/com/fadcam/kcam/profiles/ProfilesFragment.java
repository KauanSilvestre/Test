package com.fadcam.kcam.profiles;

import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fadcam.R;
import com.fadcam.SharedPreferencesManager;
import com.fadcam.kcam.miniapps.KMiniAppFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** Save the current settings as a named profile and switch between profiles. */
public class ProfilesFragment extends KMiniAppFragment {

    private ProfileStore store;
    private LinearLayout list;

    @Override
    protected int titleRes() {
        return R.string.kcam_profiles_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        store = new ProfileStore(requireContext());
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        button(body, R.string.kcam_profiles_save, v -> askName());
        LinearLayout info = card(body);
        text(info, getString(R.string.kcam_profiles_info), 12, COLOR_MUTED, false);
        render();
    }

    private void render() {
        list.removeAllViews();
        List<ProfileStore.Profile> all = store.list();
        if (all.isEmpty()) {
            TextView t = text(list, getString(R.string.kcam_profiles_empty), 14, COLOR_MUTED, false);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setPadding(0, dp(24), 0, dp(24));
            return;
        }
        for (ProfileStore.Profile p : all) {
            LinearLayout c = card(list);
            text(c, p.name, 18, 0xFFFFFFFF, true);
            text(c, getString(R.string.kcam_profiles_saved_when,
                    DateUtils.getRelativeTimeSpanString(p.savedAt)), 12, COLOR_MUTED, false);
            c.setOnClickListener(v -> confirmApply(p));
            c.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(p.name)
                        .setItems(new CharSequence[]{
                                getString(R.string.kcam_profiles_overwrite),
                                getString(R.string.kcam_profiles_delete)}, (d, which) -> {
                            if (which == 0) save(p.name);
                            else {
                                store.delete(p.id);
                                render();
                            }
                        })
                        .show();
                return true;
            });
        }
        TextView hint = text(list, getString(R.string.kcam_profiles_hint), 11, COLOR_MUTED, false);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
    }

    private void askName() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.kcam_profiles_name_hint);
        LinearLayout wrap = new LinearLayout(requireContext());
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kcam_profiles_save)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) save(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void save(String name) {
        try {
            store.saveCurrent(name);
            Toast.makeText(requireContext(), getString(R.string.kcam_profiles_saved_toast, name), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.kcam_profiles_error, Toast.LENGTH_LONG).show();
        }
        render();
    }

    private void confirmApply(ProfileStore.Profile p) {
        if (SharedPreferencesManager.getInstance(requireContext()).isRecordingInProgress()) {
            Toast.makeText(requireContext(), R.string.kcam_profiles_busy, Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.kcam_profiles_apply_title, p.name))
                .setMessage(R.string.kcam_profiles_apply_msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        store.apply(p.id);
                        Toast.makeText(requireContext(), getString(R.string.kcam_profiles_applied, p.name),
                                Toast.LENGTH_SHORT).show();
                        if (getActivity() != null) getActivity().recreate();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), R.string.kcam_profiles_error, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
