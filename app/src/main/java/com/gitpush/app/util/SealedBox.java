package com.gitpush.app.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * libsodium-compatible crypto_box_seal in pure Java (X25519 + HSalsa20/XSalsa20 + Poly1305 + BLAKE2b),
 * used to encrypt GitHub Actions secrets without native libraries.
 */
public final class SealedBox {
    private SealedBox() {}

    // ------------------------------------------------------------------ X25519
    private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger A24 = BigInteger.valueOf(121665);

    private static BigInteger le(byte[] b, int off, int len) {
        byte[] r = new byte[len + 1];
        for (int i = 0; i < len; i++) r[len - i] = b[off + i];
        return new BigInteger(r);
    }

    private static byte[] toLe(BigInteger v, int len) {
        byte[] out = new byte[len];
        byte[] be = v.toByteArray();
        for (int i = 0; i < len && i < be.length; i++) out[i] = be[be.length - 1 - i];
        return out;
    }

    public static byte[] x25519(byte[] k, byte[] u) {
        byte[] kk = k.clone();
        kk[0] &= 248; kk[31] &= 127; kk[31] |= 64;
        byte[] uu = u.clone();
        uu[31] &= 127;
        BigInteger x1 = le(uu, 0, 32).mod(P);
        BigInteger x2 = BigInteger.ONE, z2 = BigInteger.ZERO, x3 = x1, z3 = BigInteger.ONE;
        int swap = 0;
        for (int t = 254; t >= 0; t--) {
            int kt = (kk[t >> 3] >> (t & 7)) & 1;
            swap ^= kt;
            if (swap == 1) { BigInteger a = x2; x2 = x3; x3 = a; a = z2; z2 = z3; z3 = a; }
            swap = kt;
            BigInteger A = x2.add(z2).mod(P), AA = A.multiply(A).mod(P);
            BigInteger B = x2.subtract(z2).mod(P), BB = B.multiply(B).mod(P);
            BigInteger E = AA.subtract(BB).mod(P);
            BigInteger C = x3.add(z3).mod(P), D = x3.subtract(z3).mod(P);
            BigInteger DA = D.multiply(A).mod(P), CB = C.multiply(B).mod(P);
            BigInteger s = DA.add(CB).mod(P), d = DA.subtract(CB).mod(P);
            x3 = s.multiply(s).mod(P);
            z3 = x1.multiply(d.multiply(d).mod(P)).mod(P);
            x2 = AA.multiply(BB).mod(P);
            z2 = E.multiply(AA.add(A24.multiply(E)).mod(P)).mod(P);
        }
        if (swap == 1) { BigInteger a = x2; x2 = x3; x3 = a; a = z2; z2 = z3; z3 = a; }
        return toLe(x2.multiply(z2.modPow(P.subtract(BigInteger.valueOf(2)), P)).mod(P), 32);
    }

    // ------------------------------------------------------------------ Salsa20 family
    private static int rotl(int v, int c) { return (v << c) | (v >>> (32 - c)); }

    private static int ld(byte[] b, int o) { return (b[o] & 0xff) | (b[o + 1] & 0xff) << 8 | (b[o + 2] & 0xff) << 16 | (b[o + 3] & 0xff) << 24; }

    private static void st(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >>> 8); b[o + 2] = (byte) (v >>> 16); b[o + 3] = (byte) (v >>> 24); }

    private static void rounds(int[] x) {
        for (int i = 0; i < 20; i += 2) {
            x[4] ^= rotl(x[0] + x[12], 7); x[8] ^= rotl(x[4] + x[0], 9); x[12] ^= rotl(x[8] + x[4], 13); x[0] ^= rotl(x[12] + x[8], 18);
            x[9] ^= rotl(x[5] + x[1], 7); x[13] ^= rotl(x[9] + x[5], 9); x[1] ^= rotl(x[13] + x[9], 13); x[5] ^= rotl(x[1] + x[13], 18);
            x[14] ^= rotl(x[10] + x[6], 7); x[2] ^= rotl(x[14] + x[10], 9); x[6] ^= rotl(x[2] + x[14], 13); x[10] ^= rotl(x[6] + x[2], 18);
            x[3] ^= rotl(x[15] + x[11], 7); x[7] ^= rotl(x[3] + x[15], 9); x[11] ^= rotl(x[7] + x[3], 13); x[15] ^= rotl(x[11] + x[7], 18);
            x[1] ^= rotl(x[0] + x[3], 7); x[2] ^= rotl(x[1] + x[0], 9); x[3] ^= rotl(x[2] + x[1], 13); x[0] ^= rotl(x[3] + x[2], 18);
            x[6] ^= rotl(x[5] + x[4], 7); x[7] ^= rotl(x[6] + x[5], 9); x[4] ^= rotl(x[7] + x[6], 13); x[5] ^= rotl(x[4] + x[7], 18);
            x[11] ^= rotl(x[10] + x[9], 7); x[8] ^= rotl(x[11] + x[10], 9); x[9] ^= rotl(x[8] + x[11], 13); x[10] ^= rotl(x[9] + x[8], 18);
            x[12] ^= rotl(x[15] + x[14], 7); x[13] ^= rotl(x[12] + x[15], 9); x[14] ^= rotl(x[13] + x[12], 13); x[15] ^= rotl(x[14] + x[13], 18);
        }
    }

    private static final byte[] SIGMA = "expand 32-byte k".getBytes(StandardCharsets.US_ASCII);

    static byte[] hsalsa20(byte[] key, byte[] in16) {
        int[] x = new int[16];
        x[0] = ld(SIGMA, 0); x[5] = ld(SIGMA, 4); x[10] = ld(SIGMA, 8); x[15] = ld(SIGMA, 12);
        for (int i = 0; i < 4; i++) { x[1 + i] = ld(key, 4 * i); x[11 + i] = ld(key, 16 + 4 * i); x[6 + i] = ld(in16, 4 * i); }
        rounds(x);
        byte[] out = new byte[32];
        int[] idx = {0, 5, 10, 15, 6, 7, 8, 9};
        for (int i = 0; i < 8; i++) st(out, 4 * i, x[idx[i]]);
        return out;
    }

    private static byte[] xsalsa20Stream(byte[] key, byte[] nonce24, int len) {
        byte[] sub = hsalsa20(key, java.util.Arrays.copyOfRange(nonce24, 0, 16));
        byte[] out = new byte[len];
        int[] in = new int[16];
        in[0] = ld(SIGMA, 0); in[5] = ld(SIGMA, 4); in[10] = ld(SIGMA, 8); in[15] = ld(SIGMA, 12);
        for (int i = 0; i < 4; i++) { in[1 + i] = ld(sub, 4 * i); in[11 + i] = ld(sub, 16 + 4 * i); }
        in[6] = ld(nonce24, 16); in[7] = ld(nonce24, 20);
        long ctr = 0;
        for (int pos = 0; pos < len; pos += 64) {
            in[8] = (int) ctr; in[9] = (int) (ctr >>> 32);
            int[] x = in.clone();
            rounds(x);
            byte[] block = new byte[64];
            for (int i = 0; i < 16; i++) st(block, 4 * i, x[i] + in[i]);
            System.arraycopy(block, 0, out, pos, Math.min(64, len - pos));
            ctr++;
        }
        return out;
    }

    // ------------------------------------------------------------------ Poly1305
    private static byte[] poly1305(byte[] key, byte[] msg) {
        BigInteger r = le(key, 0, 16).and(new BigInteger("0ffffffc0ffffffc0ffffffc0fffffff", 16));
        BigInteger s = le(key, 16, 16);
        BigInteger p = BigInteger.ONE.shiftLeft(130).subtract(BigInteger.valueOf(5));
        BigInteger acc = BigInteger.ZERO;
        for (int i = 0; i < msg.length; i += 16) {
            int n = Math.min(16, msg.length - i);
            BigInteger v = le(msg, i, n).add(BigInteger.ONE.shiftLeft(8 * n));
            acc = acc.add(v).multiply(r).mod(p);
        }
        return toLe(acc.add(s).mod(BigInteger.ONE.shiftLeft(128)), 16);
    }

    // ------------------------------------------------------------------ BLAKE2b
    private static final long[] IV = {0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL, 0x3c6ef372fe94f82bL, 0xa54ff53a5f1d36f1L,
            0x510e527fade682d1L, 0x9b05688c2b3e6c1fL, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L};
    private static final int[][] SIG = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
            {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4}, {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
            {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13}, {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
            {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11}, {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
            {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5}, {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0},
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3}};

    private static void g(long[] v, int a, int b, int c, int d, long x, long y) {
        v[a] = v[a] + v[b] + x; v[d] = Long.rotateRight(v[d] ^ v[a], 32);
        v[c] = v[c] + v[d]; v[b] = Long.rotateRight(v[b] ^ v[c], 24);
        v[a] = v[a] + v[b] + y; v[d] = Long.rotateRight(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d]; v[b] = Long.rotateRight(v[b] ^ v[c], 63);
    }

    private static void compress(long[] h, byte[] block, long t, boolean last) {
        long[] m = new long[16];
        for (int i = 0; i < 16; i++) { long w = 0; for (int j = 7; j >= 0; j--) w = (w << 8) | (block[i * 8 + j] & 0xffL); m[i] = w; }
        long[] v = new long[16];
        System.arraycopy(h, 0, v, 0, 8); System.arraycopy(IV, 0, v, 8, 8);
        v[12] ^= t; if (last) v[14] = ~v[14];
        for (int r = 0; r < 12; r++) {
            int[] s = SIG[r];
            g(v, 0, 4, 8, 12, m[s[0]], m[s[1]]); g(v, 1, 5, 9, 13, m[s[2]], m[s[3]]);
            g(v, 2, 6, 10, 14, m[s[4]], m[s[5]]); g(v, 3, 7, 11, 15, m[s[6]], m[s[7]]);
            g(v, 0, 5, 10, 15, m[s[8]], m[s[9]]); g(v, 1, 6, 11, 12, m[s[10]], m[s[11]]);
            g(v, 2, 7, 8, 13, m[s[12]], m[s[13]]); g(v, 3, 4, 9, 14, m[s[14]], m[s[15]]);
        }
        for (int i = 0; i < 8; i++) h[i] ^= v[i] ^ v[i + 8];
    }

    static byte[] blake2b(byte[] data, int outLen) {
        long[] h = IV.clone();
        h[0] ^= 0x01010000L ^ outLen;
        int pos = 0;
        byte[] block = new byte[128];
        if (data.length == 0) compress(h, block, 0, true);
        while (pos < data.length) {
            int n = Math.min(128, data.length - pos);
            java.util.Arrays.fill(block, (byte) 0);
            System.arraycopy(data, pos, block, 0, n);
            pos += n;
            compress(h, block, pos, pos >= data.length);
        }
        byte[] out = new byte[outLen];
        for (int i = 0; i < outLen; i++) out[i] = (byte) (h[i / 8] >>> (8 * (i % 8)));
        return out;
    }

    // ------------------------------------------------------------------ crypto_box_seal
    public static byte[] seal(byte[] message, byte[] recipientPk) {
        byte[] esk = new byte[32];
        new SecureRandom().nextBytes(esk);
        return seal(message, recipientPk, esk);
    }

    public static byte[] seal(byte[] message, byte[] pk, byte[] esk) {
        byte[] base = new byte[32]; base[0] = 9;
        byte[] epk = x25519(esk, base);
        byte[] shared = x25519(esk, pk);
        byte[] key = hsalsa20(shared, new byte[16]);
        byte[] nin = new byte[64];
        System.arraycopy(epk, 0, nin, 0, 32); System.arraycopy(pk, 0, nin, 32, 32);
        byte[] nonce = blake2b(nin, 24);
        byte[] stream = xsalsa20Stream(key, nonce, 32 + message.length);
        byte[] c = new byte[message.length];
        for (int i = 0; i < c.length; i++) c[i] = (byte) (message[i] ^ stream[32 + i]);
        byte[] tag = poly1305(java.util.Arrays.copyOfRange(stream, 0, 32), c);
        byte[] out = new byte[32 + 16 + c.length];
        System.arraycopy(epk, 0, out, 0, 32); System.arraycopy(tag, 0, out, 32, 16); System.arraycopy(c, 0, out, 48, c.length);
        return out;
    }
}
