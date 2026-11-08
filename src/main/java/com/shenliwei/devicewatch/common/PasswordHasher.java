package com.shenliwei.devicewatch.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Creates and verifies versioned PBKDF2 password hashes.
 */
public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2_sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 120_000;

    private final SecureRandom random;
    private final int iterations;

    public PasswordHasher() {
        this(new SecureRandom(), ITERATIONS);
    }

    PasswordHasher(SecureRandom random, int iterations) {
        if (iterations < 10_000) {
            throw new IllegalArgumentException("iterations must be at least 10000");
        }
        this.random = random;
        this.iterations = iterations;
    }

    public String hash(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("secret must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(secret, salt, iterations);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return PREFIX
                + "$"
                + iterations
                + "$"
                + encoder.encodeToString(salt)
                + "$"
                + encoder.encodeToString(hash);
    }

    public boolean verify(char[] secret, String encodedHash) {
        if (secret == null || secret.length == 0 || encodedHash == null || encodedHash.isBlank()) {
            return false;
        }
        String[] parts = encodedHash.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int storedIterations = Integer.parseInt(parts[1]);
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expected = decoder.decode(parts[3]);
            byte[] actual = derive(secret, salt, storedIterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(char[] secret, byte[] salt, int iterationCount) {
        PBEKeySpec spec = new PBEKeySpec(secret, salt, iterationCount, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("PBKDF2 is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }
}

