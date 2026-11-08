package com.shenliwei.devicewatch.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher(new SecureRandom(), 10_000);

    @Test
    void hashVerifiesCorrectSecretAndRejectsWrongSecret() {
        char[] secret = "correct-secret".toCharArray();
        String hash = hasher.hash(secret);

        assertTrue(hasher.verify("correct-secret".toCharArray(), hash));
        assertFalse(hasher.verify("wrong-secret".toCharArray(), hash));
        assertFalse(hash.contains("correct-secret"));
    }

    @Test
    void eachHashUsesAnIndependentSalt() {
        char[] secret = "same-secret".toCharArray();
        String first = hasher.hash(secret);
        String second = hasher.hash(secret);

        assertNotEquals(first, second);
        assertTrue(hasher.verify(secret, first));
        assertTrue(hasher.verify(secret, second));
    }
}

