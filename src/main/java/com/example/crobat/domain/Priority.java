package com.example.crobat.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Lower database value is served first. */
public enum Priority {
    CRITICAL(0, "critical"),
    HIGH(1, "high"),
    NORMAL(2, "normal"),
    LOW(3, "low");

    private final int value;
    private final String jsonName;

    Priority(int value, String jsonName) {
        this.value = value;
        this.jsonName = jsonName;
    }

    public int getValue() {
        return value;
    }

    @JsonValue
    public String getJsonName() {
        return jsonName;
    }

    @JsonCreator
    public static Priority parse(String text) {
        if (text == null || text.isBlank()) {
            return NORMAL;
        }
        for (Priority priority : values()) {
            if (priority.jsonName.equalsIgnoreCase(text.trim())) {
                return priority;
            }
        }
        throw new DomainException("priority must be one of critical, high, normal, low");
    }

    public static Priority fromValue(int value) {
        for (Priority priority : values()) {
            if (priority.value == value) {
                return priority;
            }
        }
        throw new DomainException("bad priority value " + value);
    }
}
