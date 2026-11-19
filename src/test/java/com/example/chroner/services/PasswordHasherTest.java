package com.example.chroner.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    // Same seed as deploy/mysql/schema.sql for acme/admin/admin123.
    private static final String SEED =
            "pbkdf2_sha256$120000$AAECAwQFBgcICQoLDA0ODw==$qTnZDrKJXHYwEV6wcbHPg/fmXZ17uxBBfQDmEgaSE3I=";

    @Test
    void verifiesSeedPassword() {
        assertTrue(PasswordHasher.verify("admin123", SEED));
        assertFalse(PasswordHasher.verify("wrong", SEED));
    }

    @Test
    void hashesAndVerifiesNewPassword() {
        String hash = PasswordHasher.hash("another-password");
        assertTrue(PasswordHasher.verify("another-password", hash));
        assertFalse(PasswordHasher.verify("nope", hash));
    }
}
