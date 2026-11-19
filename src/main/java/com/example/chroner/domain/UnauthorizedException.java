package com.example.chroner.domain;

/** Wrong credentials or a missing token. Maps to HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
