package com.fadcam.kcam.crypt;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import androidx.documentfile.provider.DocumentFile;

import com.fadcam.Constants;
import com.fadcam.FLog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android glue for {@link KCrypt}: folders, URIs, background work. */
public final class FadCrypt {

    private static final String TAG = "KCamFadCrypt";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onProgress(String fileName, int index, int total);

        void onDone(int ok, int failed, String lastError);
    }

    private FadCrypt() {
    }

    private static File base(Context c) {
        File b = c.getExternalFilesDir(null);
        if (b == null) b = c.getFilesDir();
        return new File(b, Constants.RECORDING_DIRECTORY);
    }

    public static File encryptedDir(Context c) {
        File d = new File(base(c), "Encrypted");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    public static File decryptedDir(Context c) {
        File d = new File(base(c), "Decrypted");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    static String displayName(Context c, Uri uri) {
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) return new File(uri.getPath()).getName();
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                if (n != null) return n;
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "file";
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        int i = 1;
        while (f.exists()) {
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            f = new File(dir, stem + " (" + (i++) + ")" + ext);
        }
        return f;
    }

    private static boolean deleteUri(Context c, Uri uri) {
        try {
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) return new File(uri.getPath()).delete();
            DocumentFile df = DocumentFile.fromSingleUri(c, uri);
            if (df != null && df.delete()) return true;
            return c.getContentResolver().delete(uri, null, null) > 0;
        } catch (Exception e) {
            FLog.w(TAG, "Could not delete original " + uri, e);
            return false;
        }
    }

    public static void encrypt(Context ctx, List<Uri> uris, char[] password, boolean deleteOriginals, Callback cb) {
        Context c = ctx.getApplicationContext();
        IO.execute(() -> {
            int ok = 0, failed = 0;
            String err = null;
            for (int i = 0; i < uris.size(); i++) {
                Uri uri = uris.get(i);
                String name = displayName(c, uri);
                final int idx = i;
                MAIN.post(() -> cb.onProgress(name, idx + 1, uris.size()));
                File out = unique(encryptedDir(c), name + KCrypt.EXTENSION);
                try (InputStream in = new BufferedInputStream(c.getContentResolver().openInputStream(uri));
                     OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    KCrypt.encrypt(in, os, password, name, null);
                    ok++;
                } catch (Exception e) {
                    failed++;
                    err = e.getMessage();
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    FLog.e(TAG, "Encrypt failed: " + name, e);
                    continue;
                }
                if (deleteOriginals) deleteUri(c, uri);
            }
            java.util.Arrays.fill(password, '\0');
            final int fo = ok, ff = failed;
            final String fe = err;
            MAIN.post(() -> cb.onDone(fo, ff, fe));
        });
    }

    public interface DecryptCallback {
        void onDone(File output, Exception error);
    }

    public static void decrypt(Context ctx, File encrypted, char[] password, DecryptCallback cb) {
        Context c = ctx.getApplicationContext();
        IO.execute(() -> {
            File tmp = new File(decryptedDir(c), encrypted.getName() + ".part");
            try {
                String name;
                try (InputStream in = new BufferedInputStream(new FileInputStream(encrypted));
                     OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp))) {
                    name = KCrypt.decrypt(in, os, password, null);
                }
                if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")) {
                    name = encrypted.getName().replace(KCrypt.EXTENSION, "");
                }
                File out = unique(decryptedDir(c), name);
                if (!tmp.renameTo(out)) throw new java.io.IOException("rename failed");
                MAIN.post(() -> cb.onDone(out, null));
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                MAIN.post(() -> cb.onDone(null, e));
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
        });
    }
}
