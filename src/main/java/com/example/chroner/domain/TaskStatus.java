package com.example.chroner.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
    ACTIVE("active"),
    PAUSED("paused"),
    DELETED("deleted");

    private final String jsonName;

    TaskStatus(String jsonName) {
        this.jsonName = jsonName;
    }

    @JsonValue
    public String getJsonName() {
        return jsonName;
    }

    @JsonCreator
    public static TaskStatus parse(String text) {
        for (TaskStatus status : values()) {
            if (status.jsonName.equalsIgnoreCase(text)) {
                return status;
            }
        }
        throw new DomainException("bad task status " + text);
    }
}
