package com.example.crobat.http;

import com.example.crobat.domain.DomainException;
import com.example.crobat.domain.NotFoundException;
import com.example.crobat.domain.UnauthorizedException;
import com.example.crobat.security.Jwt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Small method + path router with JWT auth and uniform error responses. */
public class Router implements HttpHandler {
    private static final Logger LOG = Logger.getLogger(Router.class.getName());
    private static final int MAX_BODY = 256 * 1024;

    public interface ApiHandler {
        Object handle(String tenant, Map<String, String> params,
                      byte[] body, HttpExchange exchange) throws Exception;
    }

    private static class Route {
        final String method;
        final String[] pattern;
        final int status;
        final ApiHandler handler;
        final boolean publicRoute;

        Route(String method, String path, int status, ApiHandler handler,
              boolean publicRoute) {
            this.method = method;
            this.pattern = split(path);
            this.status = status;
            this.handler = handler;
            this.publicRoute = publicRoute;
        }
    }

    private final String jwtSecret;
    private final List<Route> routes = new ArrayList<>();

    public Router(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Router add(String method, String path, int status, ApiHandler handler) {
        routes.add(new Route(method, path, status, handler, false));
        return this;
    }

    public Router addPublic(String method, String path, int status, ApiHandler handler) {
        routes.add(new Route(method, path, status, handler, true));
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())
                    && "/healthz".equals(path)) {
                writeBytes(exchange, 200, "ok".getBytes(StandardCharsets.UTF_8),
                        "text/plain");
                return;
            }

            String[] parts = split(path);
            Route route = null;
            Map<String, String> params = new LinkedHashMap<>();
            for (Route candidate : routes) {
                Map<String, String> found = match(candidate, parts);
                if (found != null && candidate.method.equalsIgnoreCase(
                        exchange.getRequestMethod())) {
                    route = candidate;
                    params = found;
                    break;
                }
            }
            if (route == null) {
                writeJson(exchange, 404, Map.of("error", "not found"));
                return;
            }

            String tenant = "";
            if (!route.publicRoute) {
                tenant = Jwt.tenantFrom(
                        exchange.getRequestHeaders().getFirst("Authorization"), jwtSecret);
                if (tenant == null) {
                    writeJson(exchange, 401,
                            Map.of("error", "invalid or missing bearer token"));
                    return;
                }
            }

            byte[] body = readBody(exchange);
            Object result = route.handler.handle(tenant, params, body, exchange);
            if (result == null) {
                writeBytes(exchange, route.status, new byte[0], "application/json");
            } else {
                writeJson(exchange, route.status, result);
            }
        } catch (DomainException ex) {
            writeJson(exchange, 400, Map.of("error", ex.getMessage()));
        } catch (UnauthorizedException ex) {
            writeJson(exchange, 401, Map.of("error", ex.getMessage()));
        } catch (NotFoundException ex) {
            writeJson(exchange, 404, Map.of("error", ex.getMessage()));
        } catch (JsonProcessingException ex) {
            writeJson(exchange, 400, Map.of("error", "invalid JSON: " + ex.getOriginalMessage()));
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "request failed path="
                    + exchange.getRequestURI().getPath(), ex);
            writeJson(exchange, 500, Map.of("error", "internal error"));
        } finally {
            exchange.close();
        }
    }

    private byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            byte[] body = input.readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                throw new DomainException("request body too large");
            }
            return body;
        }
    }

    private static Map<String, String> match(Route route, String[] parts) {
        if (route.pattern.length != parts.length) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < parts.length; i++) {
            String expected = route.pattern[i];
            if (expected.startsWith("{") && expected.endsWith("}")) {
                params.put(expected.substring(1, expected.length() - 1), parts[i]);
            } else if (!expected.equals(parts[i])) {
                return null;
            }
        }
        return params;
    }

    private static String[] split(String path) {
        String value = path;
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.equals("/") || value.isEmpty()) {
            return new String[0];
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.split("/");
    }

    private static void writeJson(HttpExchange exchange, int status, Object value)
            throws IOException {
        byte[] body = Json.MAPPER.writeValueAsBytes(value);
        writeBytes(exchange, status, body, "application/json");
    }

    private static void writeBytes(HttpExchange exchange, int status,
                                   byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
