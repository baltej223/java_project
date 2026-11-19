package com.example.chroner.http;

import com.example.chroner.services.AuthService;

public final class AuthApi {
    private AuthApi() {
    }

    public static Router register(Router router, AuthService service) {
        router.addPublic("POST", "/auth/login", 200,
                (tenant, params, body, exchange) -> {
                    LoginInput input = Json.MAPPER.readValue(body, LoginInput.class);
                    return service.login(
                            input.getTenant(), input.getUsername(), input.getPassword());
                });
        return router;
    }
}
