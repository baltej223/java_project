package com.example.crobat.db;

import com.example.crobat.domain.RequestSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretsCipherTest {
    @Test
    void roundTripsHeadersAndBody() {
        SecretsCipher cipher = new SecretsCipher(new byte[32]);
        RequestSpec request = new RequestSpec("POST", "http://example.com",
                Map.of("Authorization", "Bearer xyz"), "{\"hello\":1}");

        byte[] blob = cipher.seal(request);
        RequestSpec opened = new RequestSpec();
        cipher.open(blob, opened);

        assertEquals("Bearer xyz", opened.getHeaders().get("Authorization"));
        assertEquals("{\"hello\":1}", opened.getBody());
    }

    @Test
    void emptySecretsStayNull() {
        SecretsCipher cipher = new SecretsCipher(new byte[32]);
        RequestSpec request = new RequestSpec("GET", "http://example.com", Map.of(), "");
        assertNull(cipher.seal(request));
    }

    @Test
    void wrongKeyFails() {
        SecretsCipher cipher = new SecretsCipher(new byte[32]);
        RequestSpec request = new RequestSpec("POST", "http://example.com",
                Map.of("Authorization", "Bearer xyz"), "body");
        byte[] blob = cipher.seal(request);

        byte[] otherKey = new byte[32];
        for (int i = 0; i < otherKey.length; i++) {
            otherKey[i] = 1;
        }
        SecretsCipher other = new SecretsCipher(otherKey);
        assertThrows(DatabaseException.class,
                () -> other.open(blob, new RequestSpec()));
    }
}
