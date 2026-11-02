package com.fadcam.kcam.crypt;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.webkit.MimeTypeMap;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.fadcam.R;
import com.fadcam.kcam.miniapps.KMiniAppFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FadCrypt: encrypt recordings with a password (AES-256-GCM, see {@link KCrypt}) and
 * decrypt them back. Encrypted files live in FadCam/Encrypted, decrypted copies in
 * FadCam/Decrypted. There is no password recovery — by design.
 */
public class FadCryptFragment extends KMiniAppFragment {

    public static final String ARG_URI = "encrypt_uri";

    private ActivityResultLauncher<Intent> picker;
    private LinearLayout list;
    private TextView status;
    private boolean busy;

    public static FadCryptFragment forUri(Uri uri) {
        FadCryptFragment f = new FadCryptFragment();
        Bundle b = new Bundle();
        b.putParcelable(ARG_URI, uri);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            Intent data = r.getData();
            if (data == null) return;
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) askEncrypt(uris);
        });
        if (savedInstanceState == null && getArguments() != null) {
            Uri u = androidx.core.os.BundleCompat.getParcelable(getArguments(), ARG_URI, Uri.class);
            if (u != null) {
                List<Uri> one = new ArrayList<>();
                one.add(u);
                // wait until the view exists
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (isAdded()) askEncrypt(one);
                });
            }
        }
    }

    @Override
    protected int titleRes() {
        return R.string.fadcrypt_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        icon(body, "lock", 64, COLOR_ACCENT);
        status = text(body, "", 14, COLOR_MUTED, false);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        button(body, R.string.kcam_crypt_pick, v -> {
            if (busy) return;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*", "image/*"})
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            picker.launch(i);
        });
        LinearLayout info = card(body);
        text(info, getString(R.string.kcam_crypt_info), 12, COLOR_MUTED, false);
        text(body, getString(R.string.kcam_crypt_encrypted_files), 16, 0xFFFFFFFF, true).setPadding(0, dp(12), 0, dp(8));
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        render();
    }

    private void render() {
        if (!isAdded()) return;
        list.removeAllViews();
        File[] files = FadCrypt.encryptedDir(requireContext()).listFiles((d, n) -> n.endsWith(KCrypt.EXTENSION));
        if (files == null || files.length == 0) {
            text(list, getString(R.string.kcam_crypt_empty), 13, COLOR_MUTED, false);
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            LinearLayout c = card(list);
            text(c, f.getName().replace(KCrypt.EXTENSION, ""), 14, 0xFFFFFFFF, true);
            text(c, DateUtils.getRelativeTimeSpanString(f.lastModified()) + " · "
                    + Formatter.formatShortFileSize(requireContext(), f.length()), 12, COLOR_MUTED, false);
            c.setOnClickListener(v -> askDecrypt(f));
            c.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.kcam_delete_confirm)
                        .setMessage(f.getName())
                        .setPositiveButton(R.string.kcam_delete, (d, w) -> {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                            render();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }
        text(list, getString(R.string.kcam_crypt_hint), 11, COLOR_MUTED, false).setGravity(Gravity.CENTER_HORIZONTAL);
    }

    private EditText passwordField(LinearLayout parent, int hint) {
        EditText e = new EditText(requireContext());
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setHint(hint);
        parent.addView(e);
        return e;
    }

    private void askEncrypt(List<Uri> uris) {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText p1 = passwordField(form, R.string.kcam_crypt_password);
        EditText p2 = passwordField(form, R.string.kcam_crypt_password_repeat);
        CheckBox del = new CheckBox(requireContext());
        del.setText(R.string.kcam_crypt_delete_originals);
        form.addView(del);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.kcam_crypt_encrypt_n, uris.size()))
                .setView(form)
                .setPositiveButton(R.string.kcam_crypt_encrypt, (d, w) -> {
                    String a = p1.getText().toString(), b = p2.getText().toString();
                    if (a.length() < 6) {
                        Toast.makeText(requireContext(), R.string.kcam_crypt_password_short, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!a.equals(b)) {
                        Toast.makeText(requireContext(), R.string.kcam_crypt_password_mismatch, Toast.LENGTH_LONG).show();
                        return;
                    }
                    busy = true;
                    FadCrypt.encrypt(requireContext(), uris, a.toCharArray(), del.isChecked(), new FadCrypt.Callback() {
                        @Override
                        public void onProgress(String fileName, int index, int total) {
                            if (isAdded()) status.setText(getString(R.string.kcam_crypt_working, index, total, fileName));
                        }

                        @Override
                        public void onDone(int ok, int failed, String lastError) {
                            busy = false;
                            if (!isAdded()) return;
                            status.setText(getString(R.string.kcam_crypt_done, ok, failed));
                            render();
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void askDecrypt(File f) {
        if (busy) return;
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText p = passwordField(form, R.string.kcam_crypt_password);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kcam_crypt_decrypt)
                .setView(form)
                .setPositiveButton(R.string.kcam_crypt_decrypt, (d, w) -> {
                    busy = true;
                    status.setText(R.string.kcam_crypt_decrypting);
                    FadCrypt.decrypt(requireContext(), f, p.getText().toString().toCharArray(), (out, error) -> {
                        busy = false;
                        if (!isAdded()) return;
                        if (error != null) {
                            status.setText(R.string.kcam_crypt_wrong_password);
                            return;
                        }
                        status.setText(getString(R.string.kcam_crypt_decrypted_to, out.getName()));
                        open(out);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void open(File f) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", f);
            String ext = MimeTypeMap.getFileExtensionFromUrl(f.getName());
            String mime = ext != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase()) : null;
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime != null ? mime : "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, f.getName()));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.kcam_no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
