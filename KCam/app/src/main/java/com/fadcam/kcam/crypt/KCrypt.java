package com.fadcam.kcam.crypt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * FadCrypt file format — password-based, streaming, authenticated encryption.
 *
 * <pre>
 * magic "KCRYPT01" (8) | kdf (1) | iterations (int) | salt (16) | base nonce (12)
 * record*: length (int) | AES-256-GCM ciphertext+tag
 * </pre>
 * Record 0 holds the original file name; records 1..n hold the data in 1 MiB chunks.
 * Each record's nonce is the base nonce with its index XOR-ed into the last 4 bytes, and
 * its AAD is (index, isFinal), so records cannot be reordered, dropped or truncated
 * without detection. Chunking keeps memory flat for multi-GB videos (Android's GCM
 * implementation buffers a whole message before verifying it).
 *
 * Pure Java (no Android APIs) so it is unit-tested on the JVM.
 */
public final class KCrypt {

    public static final String EXTENSION = ".kcrypt";
    static final byte[] MAGIC = "KCRYPT01".getBytes(StandardCharsets.US_ASCII);
    static final byte KDF_PBKDF2_SHA256 = 1;
    static final byte KDF_PBKDF2_SHA1 = 2;
    static final int ITERATIONS = 210_000;
    static final int CHUNK = 1 << 20;
    private static final int TAG_BITS = 128;
    private static final int MAX_RECORD = CHUNK + 64;

    public interface Progress {
        void onBytes(long processed);
    }

    /** Thrown for a wrong password or a corrupted / tampered file. */
    public static class BadPasswordOrCorruptException extends IOException {
        BadPasswordOrCorruptException(String m) {
            super(m);
        }
    }

    private KCrypt() {
    }

    public static void encrypt(InputStream in, OutputStream rawOut, char[] password, String originalName,
                               Progress progress) throws IOException, GeneralSecurityException {
        encrypt(in, rawOut, password, originalName, progress, ITERATIONS);
    }

    static void encrypt(InputStream in, OutputStream rawOut, char[] password, String originalName,
                        Progress progress, int iterations) throws IOException, GeneralSecurityException {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[16];
        byte[] nonce = new byte[12];
        rnd.nextBytes(salt);
        rnd.nextBytes(nonce);
        byte kdf = supportsSha256() ? KDF_PBKDF2_SHA256 : KDF_PBKDF2_SHA1;
        SecretKeySpec key = deriveKey(password, salt, iterations, kdf);

        DataOutputStream out = new DataOutputStream(rawOut);
        out.write(MAGIC);
        out.writeByte(kdf);
        out.writeInt(iterations);
        out.write(salt);
        out.write(nonce);

        writeRecord(out, key, nonce, 0, false, (originalName == null ? "" : originalName).getBytes(StandardCharsets.UTF_8));

        // Read one chunk ahead so the last data record can be flagged as final.
        byte[] cur = readFully(in, CHUNK);
        int index = 1;
        long done = 0;
        while (true) {
            byte[] next = cur.length == CHUNK ? readFully(in, CHUNK) : new byte[0];
            boolean last = next.length == 0;
            writeRecord(out, key, nonce, index++, last, cur);
            done += cur.length;
            if (progress != null) progress.onBytes(done);
            if (last) break;
            cur = next;
        }
        out.flush();
    }

    /** @return the original file name stored in the header. */
    public static String decrypt(InputStream rawIn, OutputStream out, char[] password, Progress progress)
            throws IOException, GeneralSecurityException {
        DataInputStream in = new DataInputStream(rawIn);
        byte[] magic = new byte[MAGIC.length];
        try {
            in.readFully(magic);
        } catch (EOFException e) {
            throw new BadPasswordOrCorruptException("Not a FadCrypt file");
        }
        if (!Arrays.equals(magic, MAGIC)) throw new BadPasswordOrCorruptException("Not a FadCrypt file");
        byte kdf = in.readByte();
        int iterations = in.readInt();
        if (iterations < 1000 || iterations > 10_000_000) throw new BadPasswordOrCorruptException("Bad header");
        byte[] salt = new byte[16];
        byte[] nonce = new byte[12];
        in.readFully(salt);
        in.readFully(nonce);
        SecretKeySpec key = deriveKey(password, salt, iterations, kdf);

        String name = new String(readRecord(in, key, nonce, 0, false), StandardCharsets.UTF_8);
        int index = 1;
        long done = 0;
        while (true) {
            int len = readLen(in);
            byte[] ct = new byte[len];
            in.readFully(ct);
            byte[] pt;
            boolean last;
            try {
                pt = open(key, nonce, index, false, ct);
                last = false;
            } catch (AEADBadTagException notIntermediate) {
                pt = open(key, nonce, index, true, ct); // throws if neither matches
                last = true;
            }
            out.write(pt);
            done += pt.length;
            if (progress != null) progress.onBytes(done);
            index++;
            if (last) break;
        }
        if (in.read() != -1) throw new BadPasswordOrCorruptException("Trailing data");
        out.flush();
        return name;
    }

    /** Checks only the header record — fast password verification. */
    public static String readName(InputStream rawIn, char[] password) throws IOException, GeneralSecurityException {
        DataInputStream in = new DataInputStream(rawIn);
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new BadPasswordOrCorruptException("Not a FadCrypt file");
        byte kdf = in.readByte();
        int iterations = in.readInt();
        byte[] salt = new byte[16];
        byte[] nonce = new byte[12];
        in.readFully(salt);
        in.readFully(nonce);
        return new String(readRecord(in, deriveKey(password, salt, iterations, kdf), nonce, 0, false),
                StandardCharsets.UTF_8);
    }

    // ── internals ───────────────────────────────────────────────────

    private static boolean supportsSha256() {
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return true;
        } catch (GeneralSecurityException e) {
            return false; // Android < 8.0
        }
    }

    static SecretKeySpec deriveKey(char[] password, byte[] salt, int iterations, byte kdf)
            throws GeneralSecurityException {
        String alg = kdf == KDF_PBKDF2_SHA1 ? "PBKDF2WithHmacSHA1" : "PBKDF2WithHmacSHA256";
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            byte[] k = SecretKeyFactory.getInstance(alg).generateSecret(spec).getEncoded();
            return new SecretKeySpec(k, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] nonceFor(byte[] base, int index) {
        byte[] n = base.clone();
        n[8] ^= (byte) (index >>> 24);
        n[9] ^= (byte) (index >>> 16);
        n[10] ^= (byte) (index >>> 8);
        n[11] ^= (byte) index;
        return n;
    }

    private static byte[] aad(int index, boolean last) {
        return ByteBuffer.allocate(8).putInt(index).putInt(last ? 1 : 0).array();
    }

    private static void writeRecord(DataOutputStream out, SecretKeySpec key, byte[] base, int index, boolean last,
                                    byte[] pt) throws IOException, GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonceFor(base, index)));
        c.updateAAD(aad(index, last));
        byte[] ct = c.doFinal(pt);
        out.writeInt(ct.length);
        out.write(ct);
    }

    private static byte[] readRecord(DataInputStream in, SecretKeySpec key, byte[] base, int index, boolean last)
            throws IOException, GeneralSecurityException {
        int len = readLen(in);
        byte[] ct = new byte[len];
        in.readFully(ct);
        try {
            return open(key, base, index, last, ct);
        } catch (AEADBadTagException e) {
            throw new BadPasswordOrCorruptException("Wrong password or corrupted file");
        }
    }

    private static int readLen(DataInputStream in) throws IOException {
        int len;
        try {
            len = in.readInt();
        } catch (EOFException e) {
            throw new BadPasswordOrCorruptException("File is truncated");
        }
        if (len < 16 || len > MAX_RECORD) throw new BadPasswordOrCorruptException("Corrupted record");
        return len;
    }

    private static byte[] open(SecretKeySpec key, byte[] base, int index, boolean last, byte[] ct)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonceFor(base, index)));
        c.updateAAD(aad(index, last));
        return c.doFinal(ct);
    }

    private static byte[] readFully(InputStream in, int max) throws IOException {
        byte[] buf = new byte[max];
        int off = 0;
        while (off < max) {
            int n = in.read(buf, off, max - off);
            if (n < 0) break;
            off += n;
        }
        return off == max ? buf : Arrays.copyOf(buf, off);
    }
}
