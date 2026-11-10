package com.example.crobat.domain;

import java.time.Instant;

public class Result {
    private int httpStatus;
    private String body = "";
    private String error = "";
    private Instant startedAt;
    private Instant finishedAt;

    public Result() {
    }

    public boolean ok() {
        return (error == null || error.isEmpty())
                && httpStatus >= 200
                && httpStatus < 300;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body == null ? "" : body;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error == null ? "" : error;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
