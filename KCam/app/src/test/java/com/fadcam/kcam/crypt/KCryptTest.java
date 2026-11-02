package com.fadcam.kcam.crypt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;

public class KCryptTest {

    private static final int FAST_ITER = 1000; // keep tests quick

    private static byte[] enc(byte[] data, String pw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        KCrypt.encrypt(new ByteArrayInputStream(data), out, pw.toCharArray(), "clip.mp4", null, FAST_ITER);
        return out.toByteArray();
    }

    private static byte[] dec(byte[] blob, String pw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String name = KCrypt.decrypt(new ByteArrayInputStream(blob), out, pw.toCharArray(), null);
        assertEquals("clip.mp4", name);
        return out.toByteArray();
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        new Random(42).nextBytes(b);
        return b;
    }

    @Test
    public void roundTrip_sizes() throws Exception {
        for (int n : new int[]{0, 1, 1000, KCrypt.CHUNK - 1, KCrypt.CHUNK, KCrypt.CHUNK + 1, 3 * KCrypt.CHUNK + 7}) {
            byte[] data = random(n);
            assertArrayEquals("size " + n, data, dec(enc(data, "secret"), "secret"));
        }
    }

    @Test
    public void wrongPassword_fails() throws Exception {
        byte[] blob = enc(random(5000), "right");
        try {
            dec(blob, "wrong");
            fail("expected failure");
        } catch (KCrypt.BadPasswordOrCorruptException expected) {
            // ok
        }
    }

    @Test
    public void truncation_isDetected() throws Exception {
        byte[] blob = enc(random(2 * KCrypt.CHUNK + 10), "pw");
        // drop the final record entirely: the previous record is not flagged final
        int lastLen = 10 + 16;
        byte[] cut = Arrays.copyOf(blob, blob.length - lastLen - 4);
        try {
            dec(cut, "pw");
            fail("expected truncation to be detected");
        } catch (Exception expected) {
            // ok (truncated or auth failure)
        }
    }

    @Test
    public void tamper_isDetected() throws Exception {
        byte[] blob = enc(random(4000), "pw");
        blob[blob.length - 20] ^= 1;
        try {
            dec(blob, "pw");
            fail("expected tamper detection");
        } catch (Exception expected) {
            // ok
        }
    }

    @Test
    public void readName_checksPasswordQuickly() throws Exception {
        byte[] blob = enc(random(10), "pw");
        assertEquals("clip.mp4", KCrypt.readName(new ByteArrayInputStream(blob), "pw".toCharArray()));
    }
}
