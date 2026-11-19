package com.example.chroner.call;

import com.example.chroner.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsrfGuardTest {
    private final SsrfGuard guard = new SsrfGuard("");

    @Test
    void blocksLoopbackAndMetadata() {
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://127.0.0.1/")));
        assertThrows(DomainException.class,
                () -> guard.check(URI.create("http://169.254.169.254/latest")));
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://localhost/")));
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://[::1]/")));
    }

    @Test
    void blocksPrivateAndSpecialRanges() {
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://10.0.0.1/")));
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://172.16.0.1/")));
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://192.168.1.1/")));
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://100.64.0.1/")));
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://198.18.0.1/")));
        assertThrows(DomainException.class, () -> guard.check(URI.create("http://0.0.0.0/")));
    }

    @Test
    void blocksNonStandardPorts() {
        assertThrows(DomainException.class,
                () -> guard.check(URI.create("http://8.8.8.8:8080/")));
    }

    @Test
    void allowsPublicHttpAndHttps() {
        assertDoesNotThrow(() -> guard.check(URI.create("http://8.8.8.8/")));
        assertDoesNotThrow(() -> guard.check(URI.create("https://8.8.8.8/")));
    }

    @Test
    void allowlistIsForDevelopment() {
        SsrfGuard dev = new SsrfGuard("172.28.0.100/32");
        assertDoesNotThrow(() -> dev.check(URI.create("http://172.28.0.100/")));
        assertThrows(DomainException.class,
                () -> dev.check(URI.create("http://172.28.0.101/")));
    }
}
