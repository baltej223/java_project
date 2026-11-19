package com.example.crobat.services;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256. Stored format:
 * pbkdf2_sha256$iterations$saltBase64$hashBase64
 */
public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS, HASH_BYTES);
        return "pbkdf2_sha256$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int bytes) {
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, iterations, bytes * 8);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("cannot derive password hash", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
