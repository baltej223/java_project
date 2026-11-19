package com.example.chroner.cli;

import com.example.chroner.config.Config;
import com.example.chroner.http.Json;
import com.example.chroner.security.Jwt;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tenant CLI. It only talks to the two HTTP APIs. */
public final class Cli {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String USAGE = String.join("\n",
            "chroner-java cli commands:",
            "  token <tenant>",
            "  task create [flags]",
            "  task update <id> [flags]",
            "  task list",
            "  task get <id>",
            "  task pause|resume|delete <id>",
            "  job get <id>",
            "  job history <task-id> [-limit n]",
            "  job dlq",
            "  job retry <id>",
            "",
            "create/update flags: -name -url -method -body -header -cron -at -tz",
            "                     -priority -timeout-ms -max-attempts -backoff-ms",
            "                     -idempotency-key");

    private Cli() {
    }

    public static void run(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException(USAGE);
        }
        switch (args[0]) {
            case "token":
                token(args);
                return;
            case "task":
                task(args);
                return;
            case "job":
                job(args);
                return;
            default:
                throw new IllegalArgumentException(USAGE);
        }
    }

    private static void token(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            throw new IllegalArgumentException("usage: cli token <tenant>");
        }
        String secret = new Config().requireJwtSecret();
        System.out.println(Jwt.sign(secret, args[1], 30L * 24 * 3600));
    }

    private static void task(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(USAGE);
        }
        String sub = args[1];
        if ("create".equals(sub)) {
            Flags flags = Flags.parse(args, 2);
            String idempotencyKey = flags.one("idempotency-key");
            Map<String, String> headers = new LinkedHashMap<>();
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                headers.put("Idempotency-Key", idempotencyKey);
            }
            call("POST", taskApi(), "/tasks", flags.taskBody(), headers);
            return;
        }
        if ("update".equals(sub)) {
            String id = idAt(args, 2);
            Flags flags = Flags.parse(args, 3);
            call("PUT", taskApi(), "/tasks/" + id, flags.taskBody(), Map.of());
            return;
        }
        if ("list".equals(sub)) {
            call("GET", taskApi(), "/tasks", null, Map.of());
            return;
        }
        String id = idAt(args, 2);
        switch (sub) {
            case "get":
                call("GET", taskApi(), "/tasks/" + id, null, Map.of());
                return;
            case "pause":
                call("POST", taskApi(), "/tasks/" + id + "/pause", null, Map.of());
                return;
            case "resume":
                call("POST", taskApi(), "/tasks/" + id + "/resume", null, Map.of());
                return;
            case "delete":
                call("DELETE", taskApi(), "/tasks/" + id, null, Map.of());
                return;
            default:
                throw new IllegalArgumentException(USAGE);
        }
    }

    private static void job(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(USAGE);
        }
        String sub = args[1];
        if ("dlq".equals(sub)) {
            call("GET", jobApi(), "/dlq", null, Map.of());
            return;
        }
        String id = idAt(args, 2);
        switch (sub) {
            case "get":
                call("GET", jobApi(), "/jobs/" + id, null, Map.of());
                return;
            case "retry":
                call("POST", jobApi(), "/jobs/" + id + "/retry", null, Map.of());
                return;
            case "history":
                Flags flags = Flags.parse(args, 3);
                String limit = flags.one("limit");
                String suffix = limit == null ? "" : "?limit=" + limit;
                call("GET", jobApi(), "/tasks/" + id + "/jobs" + suffix, null, Map.of());
                return;
            default:
                throw new IllegalArgumentException(USAGE);
        }
    }

    private static String idAt(String[] args, int index) {
        if (args.length <= index || args[index].startsWith("-")) {
            throw new IllegalArgumentException("missing id");
        }
        return args[index];
    }

    private static String taskApi() {
        return env("CHRONER_TASK_API", "http://localhost:8080");
    }

    private static String jobApi() {
        return env("CHRONER_JOB_API", "http://localhost:8081");
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private static void call(String method, String base, String path,
                             Object body, Map<String, String> headers) {
        String token = System.getenv("CHRONER_TOKEN");
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException(
                    "CHRONER_TOKEN is required (cli token <tenant>)");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(base + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                byte[] json = Json.MAPPER.writeValueAsBytes(body);
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(json));
            }
            HttpResponse<String> response = HTTP.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            prettyPrint(response.body());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static void prettyPrint(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            JsonNode node = Json.MAPPER.readTree(text);
            System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(node));
        } catch (Exception ex) {
            System.out.println(text);
        }
    }

    private static final class Flags {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final List<String> headers = new ArrayList<>();

        static Flags parse(String[] args, int start) {
            Flags flags = new Flags();
            int i = start;
            while (i < args.length) {
                String name = args[i];
                if (!name.startsWith("-")) {
                    throw new IllegalArgumentException("unexpected argument: " + name);
                }
                name = name.replaceFirst("^--?", "");
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("missing value for -" + name);
                }
                String value = args[i + 1];
                if ("header".equals(name)) {
                    flags.headers.add(value);
                } else {
                    flags.values.put(name, value);
                }
                i += 2;
            }
            return flags;
        }

        String one(String name) {
            return values.get(name);
        }

        Map<String, Object> taskBody() {
            Map<String, String> headerMap = new LinkedHashMap<>();
            for (String header : headers) {
                int colon = header.indexOf(':');
                if (colon < 0) {
                    throw new IllegalArgumentException(
                            "header must look like \"Name: value\"");
                }
                headerMap.put(header.substring(0, colon).trim(),
                        header.substring(colon + 1).trim());
            }

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", value("method", "POST"));
            request.put("url", value("url", ""));
            request.put("headers", headerMap);
            request.put("body", value("body", ""));

            Map<String, Object> retry = new LinkedHashMap<>();
            retry.put("max_attempts", intValue("max-attempts", 3));
            retry.put("backoff_ms", intValue("backoff-ms", 1000));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", value("name", ""));
            body.put("cron", value("cron", ""));
            body.put("timezone", value("tz", "UTC"));
            body.put("priority", value("priority", "normal"));
            body.put("request", request);
            body.put("timeout_ms", intValue("timeout-ms", 10000));
            body.put("retry", retry);
            String at = one("at");
            if (at != null && !at.isBlank()) {
                body.put("at", at);
            }
            return body;
        }

        private String value(String name, String defaultValue) {
            String value = values.get(name);
            return value == null ? defaultValue : value;
        }

        private int intValue(String name, int defaultValue) {
            String value = values.get(name);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("-" + name + " must be a number");
            }
        }
    }
}
