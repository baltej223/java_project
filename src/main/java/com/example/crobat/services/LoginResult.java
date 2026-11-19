package com.example.crobat.services;

import java.time.Instant;

public class LoginResult {
    private final String token;
    private final String tenantId;
    private final String username;
    private final Instant expiresAt;

    public LoginResult(String token, String tenantId, String username, Instant expiresAt) {
        this.token = token;
        this.tenantId = tenantId;
        this.username = username;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
