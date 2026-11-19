package com.example.crobat.http;

import com.example.crobat.security.Jwt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtTest {
    @Test
    void signsAndVerifies() {
        String token = Jwt.sign("secret", "acme", 60);
        assertEquals("acme", Jwt.tenantFrom("Bearer " + token, "secret"));
    }

    @Test
    void rejectsExpiredToken() {
        String token = Jwt.sign("secret", "acme", -1);
        assertNull(Jwt.tenantFrom("Bearer " + token, "secret"));
    }

    @Test
    void rejectsWrongSecret() {
        String token = Jwt.sign("secret", "acme", 60);
        assertNull(Jwt.tenantFrom("Bearer " + token, "other"));
    }

    @Test
    void rejectsAlgNone() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(
                "{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                "{\"sub\":\"acme\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        assertNull(Jwt.tenantFrom("Bearer " + header + "." + payload + ".", "secret"));
    }

    @Test
    void rejectsMalformedOrMissingToken() {
        assertNull(Jwt.tenantFrom(null, "secret"));
        assertNull(Jwt.tenantFrom("Bearer nope", "secret"));
    }
}
