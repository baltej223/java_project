package com.example.chroner.domain;

/** A missing row or a resource the tenant cannot see. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
