package com.fadcam.kcam.integrity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Pure-JVM tests for the integrity checksum + manifest (no Android / device needed). */
public class SessionIntegrityTest {

    @Test
    public void sha256MatchesKnownVectorAbc() throws Exception {
        String hex = SessionIntegrity.sha256(
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex);
    }

    @Test
    public void sha256OfEmptyMatchesKnownVector() throws Exception {
        String hex = SessionIntegrity.sha256(new ByteArrayInputStream(new byte[0]));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex);
    }

    @Test
    public void sha256OfFileEqualsSha256OfBytes() throws Exception {
        byte[] data = "sentinela-integrity".getBytes(StandardCharsets.UTF_8);
        File tmp = File.createTempFile("sentinela", ".bin");
        tmp.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        assertEquals(SessionIntegrity.sha256(new ByteArrayInputStream(data)),
                SessionIntegrity.sha256(tmp));
    }

    @Test
    public void detectsASingleChangedByte() throws Exception {
        String a = SessionIntegrity.sha256(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        String b = SessionIntegrity.sha256(new ByteArrayInputStream(new byte[]{1, 2, 3, 5}));
        assertTrue("hash must change when a byte changes", !a.equals(b));
    }

    @Test
    public void isoUtcIsStableAndZoneIndependent() {
        assertEquals("1970-01-01T00:00:00Z", SessionIntegrity.isoUtc(0L));
        // 1700000000 s since epoch is the widely-referenced 2023-11-14T22:13:20Z.
        assertEquals("2023-11-14T22:13:20Z", SessionIntegrity.isoUtc(1700000000000L));
    }

    @Test
    public void manifestIsValidJsonWithAllFields() throws Exception {
        String json = SessionIntegrity.buildManifest("clip.mp4", 123L, "deadbeef",
                "2026-11-02T21:30:00Z", "Sentinela 4.0.0", "Google Pixel 7", "Android 14 (API 34)");
        JSONObject o = new JSONObject(json); // throws if the JSON is invalid
        assertEquals(SessionIntegrity.SCHEMA, o.getString("schema"));
        assertEquals("clip.mp4", o.getString("file"));
        assertEquals(123L, o.getLong("sizeBytes"));
        assertEquals("deadbeef", o.getString("sha256"));
        assertEquals("2026-11-02T21:30:00Z", o.getString("recordedAt"));
        assertEquals("Sentinela 4.0.0", o.getString("app"));
        assertEquals("Google Pixel 7", o.getString("device"));
        assertEquals("Android 14 (API 34)", o.getString("android"));
        assertTrue(o.getString("note").length() > 0);
    }

    @Test
    public void manifestEscapesSpecialCharactersInFileName() throws Exception {
        String json = SessionIntegrity.buildManifest("a\"b\\c.mp4", 1L, "ab",
                "2026-11-02T21:30:00Z", "Sentinela", "dev", "Android");
        JSONObject o = new JSONObject(json);
        assertEquals("a\"b\\c.mp4", o.getString("file"));
    }
}
