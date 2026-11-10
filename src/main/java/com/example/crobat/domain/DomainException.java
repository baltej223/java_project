package com.example.crobat.domain;

/** A validation or conflict error. Maps to HTTP 400. */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
