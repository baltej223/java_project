package com.example.crobat.call;

import com.example.crobat.domain.DomainException;
import com.example.crobat.domain.Job;
import com.example.crobat.domain.Result;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Calls tenant endpoints. Redirects and proxies are disabled. */
public class HttpCaller {
    private final HttpClient client;
    private final SsrfGuard guard;

    public HttpCaller(String allowCidrs) {
        this.guard = new SsrfGuard(allowCidrs);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        return List.of(Proxy.NO_PROXY);
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                        // No proxy, so this is never called.
                    }
                })
                .build();
    }

    public Result call(Job job) {
        Result result = new Result();
        result.setStartedAt(Instant.now());
        try {
            URI uri = URI.create(job.getRequest().getUrl());
            guard.check(uri);

            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(job.getTimeoutMs()));
            String body = job.getRequest().getBody();
            if (body == null || body.isEmpty()) {
                builder.method(job.getRequest().getMethod(),
                        HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(job.getRequest().getMethod(),
                        HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
            for (Map.Entry<String, String> header : job.getRequest().getHeaders().entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            builder.header("Idempotency-Key", job.getId());
            builder.header("X-Scheduled-At", job.getScheduledAt().toString());
            builder.header("X-Attempt", Integer.toString(job.getAttempt()));
            builder.header("User-Agent", "crobat-java-executor");

            HttpResponse<InputStream> response = client.send(
                    builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            result.setHttpStatus(response.statusCode());
            try (InputStream input = response.body()) {
                byte[] bytes = input.readNBytes(1024);
                result.setBody(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (DomainException ex) {
            result.setError("destination not allowed");
        } catch (HttpTimeoutException ex) {
            result.setError("timeout");
        } catch (ConnectException ex) {
            result.setError("network error: connect");
        } catch (IOException ex) {
            result.setError("network error");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            result.setError("request failed");
        } catch (IllegalArgumentException ex) {
            result.setError("bad request");
        } catch (RuntimeException ex) {
            result.setError("request failed");
        } finally {
            result.setFinishedAt(Instant.now());
        }
        return result;
    }
}
