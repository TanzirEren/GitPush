package com.gitpush.app.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class GitHash {
    private GitHash() {}

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** Git blob SHA-1 ("blob <size>\0" + content). */
    public static String blobSha(long size, InputStream in) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(("blob " + size + "\u0000").getBytes(StandardCharsets.UTF_8));
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
            return hex(md.digest());
        } catch (NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    public static String sha256(InputStream in) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
            return hex(md.digest());
        } catch (NoSuchAlgorithmException e) { throw new IOException(e); }
    }
}
