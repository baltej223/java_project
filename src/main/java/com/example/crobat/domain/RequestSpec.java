package com.example.crobat.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The HTTP call a job makes. Headers and body may contain secrets.
 * They are accepted in JSON input but never serialized in a response.
 */
public class RequestSpec {
    private String method = "POST";
    private String url = "";
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";

    public RequestSpec() {
    }

    public RequestSpec(String method, String url, Map<String, String> headers, String body) {
        this.method = method;
        this.url = url;
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        this.body = body == null ? "" : body;
    }

    public RequestSpec copy() {
        return new RequestSpec(method, url, headers, body);
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @JsonIgnore
    public Map<String, String> getHeaders() {
        return headers;
    }

    @JsonProperty("headers")
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    }

    @JsonIgnore
    public String getBody() {
        return body;
    }

    @JsonProperty("body")
    public void setBody(String body) {
        this.body = body == null ? "" : body;
    }
}
