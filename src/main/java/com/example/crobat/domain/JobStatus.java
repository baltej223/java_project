package com.example.crobat.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum JobStatus {
    PENDING("pending"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    DEAD("dead");

    private final String jsonName;

    JobStatus(String jsonName) {
        this.jsonName = jsonName;
    }

    @JsonValue
    public String getJsonName() {
        return jsonName;
    }

    @JsonCreator
    public static JobStatus parse(String text) {
        for (JobStatus status : values()) {
            if (status.jsonName.equalsIgnoreCase(text)) {
                return status;
            }
        }
        throw new DomainException("bad job status " + text);
    }
}
