package com.filehome.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAndVerifiesPassword() {
        String encoded = hasher.hash("correct-horse-battery-staple");

        assertTrue(hasher.matches("correct-horse-battery-staple", encoded));
        assertFalse(hasher.matches("wrong-password", encoded));
    }

    @Test
    void rejectsUnreasonableIterationCounts() {
        String encoded = "pbkdf2$9999999$c2FsdA==$aGFzaA==";

        assertFalse(hasher.matches("password", encoded));
    }
}
