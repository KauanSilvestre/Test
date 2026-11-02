package com.fadcam.kcam.integrity;

import android.content.Context;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Integrity companion ("comprovante de integridade") for a finished recording: a
 * SHA-256 checksum plus a small JSON manifest written next to the file, recording
 * when and on what device it was captured.
 *
 * <p><b>What it proves:</b> that the file has not changed since the manifest was
 * written — a single changed byte changes the hash. <b>What it does NOT prove, by
 * itself:</b> who recorded it, that it is unedited footage of real events, or
 * anything about its legal value. The app must never present it as such.
 *
 * <p>The hashing and manifest-building helpers are pure Java (no Android
 * dependencies) so they can be unit-tested; {@link #writeSidecar} adds the
 * device/app fields and persists the manifest.
 */
public final class SessionIntegrity {

    /** Appended to the media file name, e.g. {@code clip.mp4.sentinela.json}. */
    public static final String SIDECAR_SUFFIX = ".sentinela.json";

    /** Schema id stored in the manifest so future readers can detect the format. */
    public static final String SCHEMA = "sentinela.integrity/1";

    private static final int BUFFER = 64 * 1024;
    private static final String TAG = "SessionIntegrity";

    private SessionIntegrity() {
    }

    /** Lowercase-hex SHA-256 of a file's bytes. */
    public static String sha256(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return sha256(in);
        }
    }

    /** Lowercase-hex SHA-256 of a stream's remaining bytes. Does not close the stream. */
    public static String sha256(InputStream in) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        byte[] buf = new byte[BUFFER];
        int n;
        while ((n = in.read(buf)) != -1) {
            md.update(buf, 0, n);
        }
        return toHex(md.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** ISO-8601 UTC timestamp, e.g. {@code 2026-11-02T21:30:00Z}. */
    public static String isoUtc(long epochMillis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(epochMillis));
    }

    /**
     * Builds the manifest JSON. Pure (no Android dependencies) so it can be
     * unit-tested; the device/app fields are supplied by {@link #writeSidecar}.
     */
    public static String buildManifest(String fileName, long sizeBytes, String sha256Hex,
                                       String recordedAtIso, String app, String device,
                                       String androidVersion) {
        try {
            JSONObject o = new JSONObject();
            o.put("schema", SCHEMA);
            o.put("file", fileName == null ? JSONObject.NULL : fileName);
            o.put("sizeBytes", sizeBytes);
            o.put("sha256", sha256Hex);
            o.put("recordedAt", recordedAtIso);
            o.put("app", app);
            o.put("device", device);
            o.put("android", androidVersion);
            o.put("note", "O SHA-256 detecta qualquer alteração do arquivo após este registro. "
                    + "Não comprova, por si só, autoria, ausência de edição anterior, "
                    + "nem validade jurídica.");
            return o.toString(2);
        } catch (JSONException e) {
            // A JSONException here means a programming error (e.g. a non-finite
            // number); surface it rather than writing a corrupt manifest.
            throw new IllegalStateException("Failed to build integrity manifest", e);
        }
    }

    /**
     * Computes the checksum of {@code media} and writes
     * {@code <name>.sentinela.json} in the same folder. Returns the sidecar file,
     * or {@code null} if the media file is missing/unreadable or writing fails.
     * Does heavy I/O (hashing) — call it off the main thread.
     *
     * @param recordedAtMillis capture time (epoch millis); pass {@code 0} for "now".
     */
    public static File writeSidecar(Context context, File media, long recordedAtMillis) {
        if (media == null || !media.isFile() || !media.canRead()) {
            return null;
        }
        File parent = media.getParentFile();
        if (parent == null) {
            return null;
        }
        try {
            String sha = sha256(media);
            long when = recordedAtMillis > 0 ? recordedAtMillis : System.currentTimeMillis();
            String json = buildManifest(media.getName(), media.length(), sha,
                    isoUtc(when), appLabel(context), deviceLabel(), androidLabel());
            File sidecar = new File(parent, media.getName() + SIDECAR_SUFFIX);
            try (FileOutputStream out = new FileOutputStream(sidecar)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return sidecar;
        } catch (Exception e) {
            com.fadcam.FLog.w(TAG, "Integrity sidecar failed for " + media.getName(), e);
            return null;
        }
    }

    /**
     * App-private folder that always holds integrity manifests. Used for
     * recordings in a custom (SAF) folder, where writing a sibling file is not
     * always possible — the manifest is kept here instead, keyed by file name.
     */
    public static File integrityDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        File dir = new File(base, "integrity");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Computes the checksum of a content/SAF media URI and writes its manifest
     * into {@link #integrityDir}. Returns the manifest file, or {@code null} on
     * failure. Reads the media read-only through the ContentResolver and never
     * modifies it. Does heavy I/O (hashing) — call it off the main thread.
     *
     * @param displayName      the media's display name (used for the manifest file name)
     * @param sizeBytes        media size if known, else {@code 0}
     * @param recordedAtMillis capture time (epoch millis); pass {@code 0} for "now"
     */
    public static File writeManifestForUri(Context context, android.net.Uri mediaUri,
                                           String displayName, long sizeBytes,
                                           long recordedAtMillis) {
        if (mediaUri == null) {
            return null;
        }
        try {
            String sha;
            try (InputStream in = context.getContentResolver().openInputStream(mediaUri)) {
                if (in == null) {
                    return null;
                }
                sha = sha256(in);
            }
            long when = recordedAtMillis > 0 ? recordedAtMillis : System.currentTimeMillis();
            String name = (displayName != null && !displayName.isEmpty()) ? displayName : "recording";
            String json = buildManifest(name, sizeBytes, sha, isoUtc(when),
                    appLabel(context), deviceLabel(), androidLabel());
            File out = new File(integrityDir(context), name + SIDECAR_SUFFIX);
            try (FileOutputStream os = new FileOutputStream(out)) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return out;
        } catch (Exception e) {
            com.fadcam.FLog.w(TAG, "Integrity manifest (uri) failed", e);
            return null;
        }
    }

    private static String appLabel(Context context) {
        String name;
        try {
            name = com.fadcam.BuildConfig.FORK_NAME;
        } catch (Throwable t) {
            name = "Sentinela";
        }
        try {
            android.content.pm.PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (pi != null && pi.versionName != null) {
                return name + " " + pi.versionName;
            }
        } catch (Throwable ignored) {
            // version unavailable: fall through to the name alone
        }
        return name;
    }

    private static String deviceLabel() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    private static String androidLabel() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }
}
