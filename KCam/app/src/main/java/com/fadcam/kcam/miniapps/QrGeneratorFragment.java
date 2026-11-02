package com.fadcam.kcam.miniapps;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.fadcam.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Offline QR code generator for text/URLs, Wi-Fi credentials and contacts (MECARD).
 * The image can be shared via the app's FileProvider.
 */
public class QrGeneratorFragment extends KMiniAppFragment {

    private enum Kind { TEXT, WIFI, CONTACT }

    private Kind kind = Kind.TEXT;
    private EditText f1, f2, f3;
    private TextView kindBtn;
    private ImageView image;
    private Bitmap bitmap;

    @Override
    protected int titleRes() {
        return R.string.mini_app_qr_generator_title;
    }

    @Override
    protected void buildBody(@NonNull LinearLayout body) {
        kindBtn = button(body, R.string.kcam_qr_kind_text, v -> {
            kind = Kind.values()[(kind.ordinal() + 1) % Kind.values().length];
            applyKind();
            regenerate();
        });
        LinearLayout c = card(body);
        f1 = field(c);
        f2 = field(c);
        f3 = field(c);
        image = new ImageView(requireContext());
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(0xFFFFFFFF);
        image.setPadding(dp(12), dp(12), dp(12), dp(12));
        addView(body, image, 300);
        button(body, R.string.kcam_qr_share, v -> share());
        applyKind();
        regenerate();
    }

    private EditText field(LinearLayout parent) {
        EditText e = new EditText(requireContext());
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(COLOR_MUTED);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { regenerate(); }
        });
        parent.addView(e);
        return e;
    }

    private void applyKind() {
        switch (kind) {
            case WIFI:
                kindBtn.setText(R.string.kcam_qr_kind_wifi);
                setup(f1, R.string.kcam_qr_wifi_ssid, InputType.TYPE_CLASS_TEXT);
                setup(f2, R.string.kcam_qr_wifi_password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                setup(f3, 0, 0);
                break;
            case CONTACT:
                kindBtn.setText(R.string.kcam_qr_kind_contact);
                setup(f1, R.string.kcam_qr_contact_name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                setup(f2, R.string.kcam_qr_contact_phone, InputType.TYPE_CLASS_PHONE);
                setup(f3, R.string.kcam_qr_contact_email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                break;
            default:
                kindBtn.setText(R.string.kcam_qr_kind_text);
                setup(f1, R.string.kcam_qr_text_hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                setup(f2, 0, 0);
                setup(f3, 0, 0);
                break;
        }
    }

    private void setup(EditText e, int hint, int inputType) {
        e.setVisibility(hint == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
        if (hint != 0) {
            e.setHint(hint);
            e.setInputType(inputType);
        }
    }

    private static String esc(String s) {
        // Escape characters that are special in WIFI:/MECARD: payloads
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"");
    }

    private String payload() {
        String a = f1.getText().toString().trim(), b = f2.getText().toString(), c = f3.getText().toString().trim();
        switch (kind) {
            case WIFI:
                if (a.isEmpty()) return "";
                return "WIFI:T:" + (b.isEmpty() ? "nopass" : "WPA") + ";S:" + esc(a) + ";"
                        + (b.isEmpty() ? "" : "P:" + esc(b) + ";") + ";";
            case CONTACT:
                if (a.isEmpty() && b.trim().isEmpty() && c.isEmpty()) return "";
                return "MECARD:N:" + esc(a) + ";" + (b.trim().isEmpty() ? "" : "TEL:" + esc(b.trim()) + ";")
                        + (c.isEmpty() ? "" : "EMAIL:" + esc(c) + ";") + ";";
            default:
                return f1.getText().toString();
        }
    }

    private void regenerate() {
        if (image == null) return;
        String text = payload();
        if (text.isEmpty()) {
            bitmap = null;
            image.setImageDrawable(null);
            return;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 768, 768, hints);
            int w = m.getWidth(), h = m.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) px[y * w + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
            bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            image.setImageBitmap(bitmap);
        } catch (Exception e) {
            bitmap = null;
            image.setImageDrawable(null);
            Toast.makeText(requireContext(), R.string.kcam_qr_too_long, Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        if (bitmap == null) return;
        try {
            File dir = new File(requireContext().getCacheDir(), "qr");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdirs");
            File out = new File(dir, "kcam_qr.png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", out);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.kcam_qr_share)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.kcam_no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
