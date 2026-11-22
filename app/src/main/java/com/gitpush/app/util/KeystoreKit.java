package com.gitpush.app.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Android signing-key toolkit in pure Java (no BouncyCastle): self-signed X.509 certificates, JKS writer/reader
 * (Android has no JKS provider), PKCS12/BKS through the platform provider, fingerprints and PEM/Base64 helpers.
 */
public final class KeystoreKit {
    private KeystoreKit() {}

    // ================================================================== model
    public static final class Spec {
        public String algo = "RSA";          // RSA or EC
        public int bits = 2048;              // RSA bits; EC uses 256
        public String cn = "", ou = "", o = "", l = "", st = "", c = "";
        public int years = 25;
    }

    /** Key + certificate chain loaded from or built for any keystore format. */
    public static final class Material {
        public String alias;
        public PrivateKey key;
        public List<byte[]> chain = new ArrayList<>();   // DER certificates, leaf first
        public X509Certificate leaf() throws GeneralSecurityException { return parse(chain.get(0)); }
    }

    // ================================================================== base64 / hex / pem
    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    public static String b64(byte[] d) {
        StringBuilder sb = new StringBuilder((d.length + 2) / 3 * 4);
        for (int i = 0; i < d.length; i += 3) {
            int b0 = d[i] & 255, b1 = i + 1 < d.length ? d[i + 1] & 255 : 0, b2 = i + 2 < d.length ? d[i + 2] & 255 : 0;
            sb.append(B64[b0 >> 2]).append(B64[((b0 & 3) << 4) | (b1 >> 4)]);
            sb.append(i + 1 < d.length ? B64[((b1 & 15) << 2) | (b2 >> 6)] : '=');
            sb.append(i + 2 < d.length ? B64[b2 & 63] : '=');
        }
        return sb.toString();
    }

    public static byte[] unb64(String s) {
        String t = s.replaceAll("[^A-Za-z0-9+/]", "");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int buf = 0, bits = 0;
        for (int i = 0; i < t.length(); i++) {
            int v = new String(B64).indexOf(t.charAt(i));
            buf = (buf << 6) | v; bits += 6;
            if (bits >= 8) { bits -= 8; bo.write((buf >> bits) & 255); }
        }
        return bo.toByteArray();
    }

    public static String hex(byte[] d, boolean colons) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) { if (colons && i > 0) sb.append(':'); sb.append(String.format(Locale.US, "%02X", d[i] & 255)); }
        return sb.toString();
    }

    public static String pem(byte[] der, String label) {
        String b = b64(der);
        StringBuilder sb = new StringBuilder("-----BEGIN " + label + "-----\n");
        for (int i = 0; i < b.length(); i += 64) sb.append(b, i, Math.min(b.length(), i + 64)).append('\n');
        return sb.append("-----END ").append(label).append("-----\n").toString();
    }

    /** "SHA-1", "SHA-256", "MD5" fingerprint with colons. */
    public static String fingerprint(byte[] der, String alg) {
        try { return hex(MessageDigest.getInstance(alg).digest(der), true); } catch (Exception e) { return ""; }
    }

    /** Facebook-login "key hash": Base64 of the SHA-1 of the certificate. */
    public static String facebookHash(byte[] der) {
        try { return b64(MessageDigest.getInstance("SHA-1").digest(der)); } catch (Exception e) { return ""; }
    }

    private static final String PW_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    public static String randomPassword(int len) {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(PW_CHARS.charAt(r.nextInt(PW_CHARS.length())));
        return sb.toString();
    }

    // ================================================================== DER
    static byte[] len(int n) {
        if (n < 128) return new byte[]{(byte) n};
        if (n < 256) return new byte[]{(byte) 0x81, (byte) n};
        if (n < 65536) return new byte[]{(byte) 0x82, (byte) (n >> 8), (byte) n};
        return new byte[]{(byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }

    static byte[] tlv(int tag, byte[]... parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] p : parts) body.write(p, 0, p.length);
        byte[] b = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        byte[] l = len(b.length);
        out.write(l, 0, l.length);
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    static byte[] seq(byte[]... p) { return tlv(0x30, p); }
    static byte[] set(byte[]... p) { return tlv(0x31, p); }
    static byte[] integer(BigInteger v) { return tlv(0x02, v.toByteArray()); }
    static byte[] nul() { return new byte[]{0x05, 0x00}; }
    static byte[] octets(byte[] b) { return tlv(0x04, b); }
    static byte[] bitString(byte[] b) { byte[] x = new byte[b.length + 1]; System.arraycopy(b, 0, x, 1, b.length); return tlv(0x03, x); }

    static byte[] oid(String dotted) {
        String[] p = dotted.split("\\.");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(Integer.parseInt(p[0]) * 40 + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            int n = 1;
            for (long t = v >> 7; t > 0; t >>= 7) n++;
            for (int k = n - 1; k >= 0; k--) bo.write((int) (((v >> (7 * k)) & 0x7F) | (k > 0 ? 0x80 : 0)));
        }
        return tlv(0x06, bo.toByteArray());
    }

    private static byte[] rdn(String oid, String value, boolean printable) {
        byte[] v = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return set(seq(oid(oid), tlv(printable ? 0x13 : 0x0C, v)));
    }

    static byte[] name(Spec s) {
        List<byte[]> r = new ArrayList<>();
        if (!s.c.isEmpty()) r.add(rdn("2.5.4.6", s.c.toUpperCase(Locale.ROOT), true));
        if (!s.st.isEmpty()) r.add(rdn("2.5.4.8", s.st, false));
        if (!s.l.isEmpty()) r.add(rdn("2.5.4.7", s.l, false));
        if (!s.o.isEmpty()) r.add(rdn("2.5.4.10", s.o, false));
        if (!s.ou.isEmpty()) r.add(rdn("2.5.4.11", s.ou, false));
        if (!s.cn.isEmpty()) r.add(rdn("2.5.4.3", s.cn, false));
        return seq(r.toArray(new byte[0][]));
    }

    private static byte[] time(long ms) {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        java.util.Calendar cal = java.util.Calendar.getInstance(utc);
        cal.setTimeInMillis(ms);
        boolean gen = cal.get(java.util.Calendar.YEAR) >= 2050;
        SimpleDateFormat f = new SimpleDateFormat(gen ? "yyyyMMddHHmmss'Z'" : "yyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(utc);
        return tlv(gen ? 0x18 : 0x17, f.format(new Date(ms)).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public static String validate(Spec s) {
        if (s.cn.trim().isEmpty() && s.o.trim().isEmpty() && s.ou.trim().isEmpty()) return "Enter at least a name (CN), organisation (O) or unit (OU)";
        if (!s.c.isEmpty() && !s.c.matches("[A-Za-z]{2}")) return "Country must be a 2-letter code like US or BD";
        if (s.years < 1 || s.years > 100) return "Validity must be 1–100 years";
        return null;
    }

    // ================================================================== key + certificate
    public static Material generate(Spec s) throws GeneralSecurityException, IOException {
        String err = validate(s);
        if (err != null) throw new IllegalArgumentException(err);
        boolean ec = s.algo.equals("EC");
        KeyPairGenerator g = KeyPairGenerator.getInstance(ec ? "EC" : "RSA");
        if (ec) g.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom()); else g.initialize(s.bits, new SecureRandom());
        KeyPair kp = g.generateKeyPair();
        long now = System.currentTimeMillis() - 60_000L;
        long end = now + s.years * 365L * 86_400_000L + (s.years / 4) * 86_400_000L;
        byte[] sigAlg = ec ? seq(oid("1.2.840.10045.4.3.2")) : seq(oid("1.2.840.113549.1.1.11"), nul());
        byte[] serial = new byte[12];
        new SecureRandom().nextBytes(serial);
        serial[0] &= 0x7F; if (serial[0] == 0) serial[0] = 1;
        byte[] nm = name(s);
        byte[] tbs = seq(tlv(0xA0, integer(BigInteger.valueOf(2))), integer(new BigInteger(1, serial)), sigAlg, nm,
                seq(time(now), time(end)), nm, kp.getPublic().getEncoded());
        Signature sig = Signature.getInstance(ec ? "SHA256withECDSA" : "SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbs);
        byte[] cert = seq(tbs, sigAlg, bitString(sig.sign()));
        Material m = new Material();
        m.key = kp.getPrivate();
        m.chain.add(cert);
        parse(cert).verify(kp.getPublic());   // self-check, throws if the DER is malformed
        return m;
    }

    public static X509Certificate parse(byte[] der) throws GeneralSecurityException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    // ================================================================== JKS writer / reader
    private static final String JKS_WHITENER = "Mighty Aphrodite";
    private static final String KEY_PROTECTOR_OID = "1.3.6.1.4.1.42.2.17.1.1";

    private static byte[] utf16be(char[] pw) {
        byte[] b = new byte[pw.length * 2];
        for (int i = 0; i < pw.length; i++) { b[2 * i] = (byte) (pw[i] >> 8); b[2 * i + 1] = (byte) pw[i]; }
        return b;
    }

    private static byte[] sha1(byte[]... parts) throws GeneralSecurityException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        for (byte[] p : parts) md.update(p);
        return md.digest();
    }

    /** Sun's proprietary private-key protection (the algorithm inside every .jks). */
    static byte[] protectKey(byte[] pkcs8, char[] password) throws GeneralSecurityException {
        byte[] pw = utf16be(password);
        byte[] salt = new byte[20];
        new SecureRandom().nextBytes(salt);
        byte[] xor = new byte[pkcs8.length];
        byte[] digest = salt;
        for (int off = 0; off < pkcs8.length; off += 20) {
            digest = sha1(pw, digest);
            System.arraycopy(digest, 0, xor, off, Math.min(20, pkcs8.length - off));
        }
        byte[] enc = new byte[salt.length + pkcs8.length + 20];
        System.arraycopy(salt, 0, enc, 0, 20);
        for (int i = 0; i < pkcs8.length; i++) enc[20 + i] = (byte) (pkcs8[i] ^ xor[i]);
        byte[] check = sha1(pw, pkcs8);
        System.arraycopy(check, 0, enc, 20 + pkcs8.length, 20);
        return seq(seq(oid(KEY_PROTECTOR_OID), nul()), octets(enc));   // EncryptedPrivateKeyInfo
    }

    static byte[] unprotectKey(byte[] encryptedPrivateKeyInfo, char[] password) throws GeneralSecurityException, IOException {
        Der d = new Der(encryptedPrivateKeyInfo);
        Der top = d.next();                 // SEQUENCE
        top.next();                         // AlgorithmIdentifier
        byte[] enc = top.next().content();  // OCTET STRING
        if (enc.length < 40) throw new GeneralSecurityException("Bad protected key");
        byte[] pw = utf16be(password);
        byte[] salt = Arrays.copyOfRange(enc, 0, 20);
        int n = enc.length - 40;
        byte[] plain = new byte[n];
        byte[] digest = salt;
        for (int off = 0; off < n; off += 20) {
            digest = sha1(pw, digest);
            for (int i = 0; i < 20 && off + i < n; i++) plain[off + i] = (byte) (enc[20 + off + i] ^ digest[i]);
        }
        byte[] check = sha1(pw, plain);
        if (!Arrays.equals(check, Arrays.copyOfRange(enc, 20 + n, enc.length))) throw new GeneralSecurityException("Wrong key password");
        return plain;
    }

    public static byte[] writeJks(String alias, PrivateKey key, List<byte[]> chain, char[] storePass, char[] keyPass) throws GeneralSecurityException, IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bo);
        out.writeInt(0xFEEDFEED); out.writeInt(2); out.writeInt(1);
        out.writeInt(1);
        out.writeUTF(alias.toLowerCase(Locale.ROOT));
        out.writeLong(System.currentTimeMillis());
        byte[] prot = protectKey(key.getEncoded(), keyPass);
        out.writeInt(prot.length); out.write(prot);
        out.writeInt(chain.size());
        for (byte[] c : chain) { out.writeUTF("X.509"); out.writeInt(c.length); out.write(c); }
        out.flush();
        byte[] body = bo.toByteArray();
        byte[] dig = sha1(utf16be(storePass), JKS_WHITENER.getBytes("UTF-8"), body);
        byte[] all = Arrays.copyOf(body, body.length + 20);
        System.arraycopy(dig, 0, all, body.length, 20);
        return all;
    }

    /** Parses a .jks: verifies the store password and returns every key entry (key decrypted with [keyPass]). */
    public static List<Material> readJks(byte[] data, char[] storePass, char[] keyPass) throws GeneralSecurityException, IOException {
        if (data.length < 32) throw new IOException("Not a JKS file");
        byte[] body = Arrays.copyOf(data, data.length - 20);
        byte[] want = sha1(utf16be(storePass), JKS_WHITENER.getBytes("UTF-8"), body);
        if (!Arrays.equals(want, Arrays.copyOfRange(data, data.length - 20, data.length))) throw new GeneralSecurityException("Wrong keystore password (or the file is damaged)");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
        if (in.readInt() != 0xFEEDFEED) throw new IOException("Not a JKS file");
        int ver = in.readInt(), count = in.readInt();
        List<Material> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int tag = in.readInt();
            String alias = in.readUTF();
            in.readLong();
            Material m = new Material();
            m.alias = alias;
            if (tag == 1) {
                byte[] prot = new byte[in.readInt()]; in.readFully(prot);
                int n = in.readInt();
                for (int k = 0; k < n; k++) {
                    if (ver == 2) in.readUTF();
                    byte[] c = new byte[in.readInt()]; in.readFully(c);
                    m.chain.add(c);
                }
                byte[] pkcs8 = unprotectKey(prot, keyPass);
                m.key = privateKey(pkcs8);
                out.add(m);
            } else if (tag == 2) {
                if (ver == 2) in.readUTF();
                byte[] c = new byte[in.readInt()]; in.readFully(c);   // trusted cert entry – no key
            } else throw new IOException("Unknown JKS entry");
        }
        return out;
    }

    static PrivateKey privateKey(byte[] pkcs8) throws GeneralSecurityException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8);
        try { return KeyFactory.getInstance("RSA").generatePrivate(spec); }
        catch (GeneralSecurityException e) { return KeyFactory.getInstance("EC").generatePrivate(spec); }
    }

    // ================================================================== platform keystores (PKCS12 / BKS)
    public static boolean typeAvailable(String type) {
        try { KeyStore.getInstance(type); return true; } catch (Exception e) { return false; }
    }

    public static byte[] write(String type, Material m, char[] storePass, char[] keyPass) throws GeneralSecurityException, IOException {
        if (type.equals("JKS")) return writeJks(m.alias, m.key, m.chain, storePass, keyPass);
        KeyStore ks = KeyStore.getInstance(type);
        ks.load(null, null);
        Certificate[] chain = new Certificate[m.chain.size()];
        for (int i = 0; i < chain.length; i++) chain[i] = parse(m.chain.get(i));
        ks.setKeyEntry(m.alias.toLowerCase(Locale.ROOT), m.key, type.equals("PKCS12") ? storePass : keyPass, chain);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ks.store(bo, storePass);
        return bo.toByteArray();
    }

    /** Detects JKS / PKCS12 / BKS and returns the first key entry (or the one named [alias]). */
    public static Material read(byte[] data, char[] storePass, char[] keyPass, String alias) throws GeneralSecurityException, IOException {
        if (data.length > 4 && (data[0] & 255) == 0xFE && (data[1] & 255) == 0xED && (data[2] & 255) == 0xFE && (data[3] & 255) == 0xED) {
            for (Material m : readJks(data, storePass, keyPass)) if (alias == null || alias.isEmpty() || m.alias.equalsIgnoreCase(alias)) return m;
            throw new GeneralSecurityException("No key entry with that alias in the keystore");
        }
        String[] types = (data.length > 0 && (data[0] & 255) == 0x30) ? new String[]{"PKCS12", "BKS"} : new String[]{"BKS", "PKCS12"};
        GeneralSecurityException last = null;
        for (String t : types) {
            try {
                KeyStore ks = KeyStore.getInstance(t);
                ks.load(new ByteArrayInputStream(data), storePass);
                java.util.Enumeration<String> en = ks.aliases();
                while (en.hasMoreElements()) {
                    String a = en.nextElement();
                    if (!ks.isKeyEntry(a) || (alias != null && !alias.isEmpty() && !a.equalsIgnoreCase(alias))) continue;
                    Material m = new Material();
                    m.alias = a;
                    m.key = (PrivateKey) ks.getKey(a, t.equals("PKCS12") ? storePass : keyPass);
                    for (Certificate c : ks.getCertificateChain(a)) m.chain.add(c.getEncoded());
                    return m;
                }
                throw new GeneralSecurityException("No key entry with that alias in the keystore");
            } catch (GeneralSecurityException | IOException e) {
                if (e instanceof GeneralSecurityException) last = (GeneralSecurityException) e; else last = new GeneralSecurityException(e.getMessage() == null ? "Wrong password or unsupported file" : e.getMessage());
            }
        }
        throw last != null ? last : new GeneralSecurityException("Could not read this keystore");
    }

    public static String detectType(byte[] d) {
        if (d.length > 4 && (d[0] & 255) == 0xFE && (d[1] & 255) == 0xED) return "JKS";
        if (d.length > 0 && (d[0] & 255) == 0x30) return "PKCS12";
        return "BKS";
    }

    // ================================================================== key.txt
    public static String describe(X509Certificate c) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "Subject: " + c.getSubjectX500Principal().getName() + "\nValid: " + f.format(c.getNotBefore()) + " → " + f.format(c.getNotAfter())
                + "\nAlgorithm: " + c.getPublicKey().getAlgorithm() + "  (" + c.getSigAlgName() + ")";
    }

    // ================================================================== tiny DER reader
    static final class Der {
        private final byte[] b; private int pos; int tag; private int start, end;
        Der(byte[] b) { this.b = b; this.pos = 0; this.end = b.length; }
        private Der(byte[] b, int s, int e) { this.b = b; this.pos = s; this.start = s; this.end = e; }
        Der next() throws IOException {
            if (pos >= end) throw new IOException("DER overrun");
            int t = b[pos++] & 255;
            int l = b[pos++] & 255;
            if ((l & 0x80) != 0) { int n = l & 0x7F; l = 0; for (int i = 0; i < n; i++) l = (l << 8) | (b[pos++] & 255); }
            Der d = new Der(b, pos, pos + l);
            d.tag = t;
            pos += l;
            return d;
        }
        byte[] content() { return Arrays.copyOfRange(b, start, end); }
    }
}
