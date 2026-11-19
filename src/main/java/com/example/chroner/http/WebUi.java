package com.example.chroner.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves the built React files from WEB_ROOT. Paths under the API prefixes go
 * to the normal JSON router; every other path falls back to index.html so
 * React Router works after a refresh.
 */
public class WebUi implements HttpHandler {
    private static final Logger LOG = Logger.getLogger(WebUi.class.getName());
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("txt", "text/plain; charset=utf-8"));

    private final Router api;
    private final Path root;

    public WebUi(Router api, Path root) {
        this.api = api;
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        // A browser refreshing /tasks or /tasks/123 wants the SPA, not JSON.
        // fetch() sends Accept: */* by default, so those requests still hit the API.
        boolean browserPage = "GET".equalsIgnoreCase(exchange.getRequestMethod())
                && acceptsHtml(exchange);
        if (path.equals("/healthz") || (isApi(path) && !browserPage)) {
            api.handle(exchange);
            return;
        }
        try {
            serve(exchange, path);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "static file failed path=" + path, ex);
            throw ex;
        } finally {
            exchange.close();
        }
    }

    private static boolean acceptsHtml(HttpExchange exchange) {
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        return accept != null && accept.contains("text/html");
    }

    private static boolean isApi(String path) {
        return path.equals("/healthz")
                || path.equals("/tasks") || path.startsWith("/tasks/")
                || path.equals("/jobs") || path.startsWith("/jobs/")
                || path.equals("/dlq") || path.startsWith("/dlq/")
                || path.equals("/auth") || path.startsWith("/auth/");
    }

    private void serve(HttpExchange exchange, String path) throws IOException {
        if (!Files.isDirectory(root)) {
            sendHtml(exchange, 503, missingBuildPage());
            return;
        }

        String relative = path.equals("/") ? "index.html" : path.substring(1);
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) {
            sendHtml(exchange, 404, "<h1>Not found</h1>");
            return;
        }
        if (Files.isDirectory(file)) {
            file = file.resolve("index.html");
        }
        if (!Files.isRegularFile(file)) {
            // A React Router path such as /tasks/123 has no extension. Serve the app.
            if (!relative.contains(".")) {
                file = root.resolve("index.html");
            }
        }
        if (!Files.isRegularFile(file)) {
            if ("index.html".equals(file.getFileName().toString())) {
                sendHtml(exchange, 503, missingBuildPage());
            } else {
                sendHtml(exchange, 404, "<h1>Not found</h1>");
            }
            return;
        }

        byte[] body = Files.readAllBytes(file);
        String name = file.getFileName().toString();
        String type = contentType(name);
        exchange.getResponseHeaders().set("Content-Type", type);
        if ("index.html".equals(name)) {
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        } else {
            exchange.getResponseHeaders().set(
                    "Cache-Control", "public, max-age=31536000, immutable");
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String contentType(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String type = CONTENT_TYPES.get(name.substring(dot + 1).toLowerCase());
            if (type != null) {
                return type;
            }
        }
        return "application/octet-stream";
    }

    private static String missingBuildPage() {
        return "<!doctype html><html><body style=\"font-family:system-ui;padding:2rem;"
                + "max-width:42rem;margin:auto\">"
                + "<h1>Chroner UI is not built</h1>"
                + "<p>Build the frontend first:</p>"
                + "<pre>cd frontend\nnpm install\nnpm run build</pre>"
                + "<p>Then start the app with <code>WEB_ROOT=frontend/dist</code>.</p>"
                + "</body></html>";
    }

    private static void sendHtml(HttpExchange exchange, int status, String html)
            throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
