package com.fadcam.kcam.profiles;

import android.content.Context;

import com.fadcam.util.PreferencesBackupUtil;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Settings profiles: named snapshots of the app preferences ("Car", "Bodycam", "Night"…),
 * stored as JSON in app-private storage using the upstream backup format
 * ({@link PreferencesBackupUtil}).
 *
 * <p>Security-related keys (app lock, remote password, tokens) are never taken from a
 * profile: applying one keeps the current values, so an old profile cannot silently
 * restore an old PIN or password.
 */
public final class ProfileStore {

    private static final Pattern SECURITY_KEY = Pattern.compile(
            "(?i).*(applock|app_lock|password|passcode|_pin|pin_|biometric|secret|token|auth_|tab_lock).*");
    private static final String DIR = "kcam_profiles";

    private final Context context;

    public ProfileStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public static boolean isSecurityKey(String key) {
        return SECURITY_KEY.matcher(key).matches();
    }

    private File dir() {
        File d = new File(context.getFilesDir(), DIR);
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    /** File-system-safe id derived from the display name. */
    static String idFor(String name) {
        String id = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        id = id.replaceAll("^_+|_+$", "");
        return id.isEmpty() ? "profile" : id;
    }

    public static final class Profile {
        public final String id;
        public final String name;
        public final long savedAt;

        Profile(String id, String name, long savedAt) {
            this.id = id;
            this.name = name;
            this.savedAt = savedAt;
        }
    }

    public List<Profile> list() {
        List<Profile> out = new ArrayList<>();
        File[] files = dir().listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : files) {
            try {
                JSONObject o = read(f);
                out.add(new Profile(f.getName().replace(".json", ""), o.optString("name", f.getName()),
                        o.optLong("savedAt", f.lastModified())));
            } catch (Exception ignored) {
                // skip unreadable files
            }
        }
        return out;
    }

    /** Snapshot the current settings under {@code name} (overwrites a profile with the same id). */
    public void saveCurrent(String name) throws Exception {
        JSONObject prefs = PreferencesBackupUtil.buildBackupJson(context);
        List<String> drop = new ArrayList<>();
        for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
            String k = it.next();
            if (isSecurityKey(k)) drop.add(k);
        }
        for (String k : drop) prefs.remove(k);
        JSONObject root = new JSONObject()
                .put("name", name.trim())
                .put("savedAt", System.currentTimeMillis())
                .put("prefs", prefs);
        write(new File(dir(), idFor(name) + ".json"), root);
    }

    /** Applies the profile, keeping the current security-related values. */
    public void apply(String id) throws Exception {
        JSONObject root = read(new File(dir(), id + ".json"));
        JSONObject prefs = root.getJSONObject("prefs");
        JSONObject current = PreferencesBackupUtil.buildBackupJson(context);
        List<String> drop = new ArrayList<>();
        for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
            String k = it.next();
            if (isSecurityKey(k)) drop.add(k);
        }
        for (String k : drop) prefs.remove(k);
        for (Iterator<String> it = current.keys(); it.hasNext(); ) {
            String k = it.next();
            if (isSecurityKey(k)) prefs.put(k, current.get(k));
        }
        PreferencesBackupUtil.applyFromJson(context, prefs);
    }

    public boolean delete(String id) {
        return new File(dir(), id + ".json").delete();
    }

    private static JSONObject read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return new JSONObject(new String(buf, 0, off, StandardCharsets.UTF_8));
        }
    }

    private static void write(File f, JSONObject o) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) {
            throw new IOException("rename failed");
        }
    }
}
