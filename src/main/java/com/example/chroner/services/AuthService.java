package com.example.chroner.services;

import com.example.chroner.db.UserStore;
import com.example.chroner.domain.UnauthorizedException;
import com.example.chroner.security.Jwt;

import java.time.Instant;

/** Verifies username/password and mints the same JWT the CLI uses. */
public class AuthService {
    private final UserStore users;
    private final String jwtSecret;
    private final long tokenTtlSeconds;

    public AuthService(UserStore users, String jwtSecret, long tokenTtlSeconds) {
        this.users = users;
        this.jwtSecret = jwtSecret;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public LoginResult login(String tenantId, String username, String password) {
        String tenant = tenantId == null ? "" : tenantId.trim();
        String name = username == null ? "" : username.trim();
        if (tenant.isEmpty() || name.isEmpty() || password == null || password.isEmpty()) {
            throw invalidCredentials();
        }

        UserStore.User user = users.find(tenant, name);
        if (user == null || !PasswordHasher.verify(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }

        String token = Jwt.sign(jwtSecret, user.getTenantId(), tokenTtlSeconds);
        Instant expiresAt = Instant.now().plusSeconds(tokenTtlSeconds);
        return new LoginResult(token, user.getTenantId(), user.getUsername(), expiresAt);
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("invalid tenant, username or password");
    }
}
