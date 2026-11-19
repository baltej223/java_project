package com.example.chroner.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/** Demo target: /fail, /flaky, /slow, and default 200. */
public class EchoApi implements HttpHandler {
    private static final Logger LOG = Logger.getLogger(EchoApi.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String attemptHeader = exchange.getRequestHeaders().getFirst("X-Attempt");
            int attempt;
            try {
                attempt = attemptHeader == null ? 0 : Integer.parseInt(attemptHeader);
            } catch (NumberFormatException ex) {
                attempt = 0;
            }
            String scheduled = exchange.getRequestHeaders().getFirst("X-Scheduled-At");
            long lagMs = 0;
            try {
                lagMs = Duration.between(Instant.parse(scheduled), Instant.now()).toMillis();
            } catch (Exception ignored) {
                // Missing or malformed header in a manual call.
            }
            LOG.info("received method=" + exchange.getRequestMethod()
                    + " path=" + path
                    + " idempotency_key="
                    + exchange.getRequestHeaders().getFirst("Idempotency-Key")
                    + " attempt=" + attempt
                    + " lag_ms=" + lagMs);

            if ("/fail".equals(path)) {
                send(exchange, 500, "boom");
                return;
            }
            if ("/flaky".equals(path) && attempt < 2) {
                send(exchange, 503, "try again");
                return;
            }
            if ("/slow".equals(path)) {
                long ms = queryMs(exchange.getRequestURI().getRawQuery());
                sleep(ms);
                send(exchange, 200, "ok, slowly");
                return;
            }
            if ("/flaky".equals(path)) {
                send(exchange, 200, "ok on retry");
                return;
            }
            send(exchange, 200, "ok");
        } finally {
            exchange.close();
        }
    }

    private static long queryMs(String query) {
        if (query == null) {
            return 0;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("ms")) {
                try {
                    return Long.parseLong(pair[1]);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void send(HttpExchange exchange, int status, String text)
            throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
