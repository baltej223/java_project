package com.example.crobat.http;

import com.example.crobat.services.JobService;

public final class JobApi {
    private JobApi() {
    }

    public static Router router(JobService service, String jwtSecret) {
        return register(new Router(jwtSecret), service);
    }

    /** Adds the job routes to an existing router (used by the dashboard server). */
    public static Router register(Router router, JobService service) {
        router.add("GET", "/jobs/{id}", 200, (tenant, params, body, exchange) ->
                service.get(tenant, params.get("id")));

        router.add("POST", "/jobs/{id}/retry", 200, (tenant, params, body, exchange) ->
                service.retry(tenant, params.get("id")));

        router.add("GET", "/tasks/{id}/jobs", 200, (tenant, params, body, exchange) -> {
            int limit = queryLimit(exchange.getRequestURI().getRawQuery());
            return service.taskHistory(tenant, params.get("id"), limit);
        });

        router.add("GET", "/dlq", 200, (tenant, params, body, exchange) ->
                service.dlq(tenant));

        return router;
    }

    private static int queryLimit(String query) {
        int limit = 50;
        if (query == null) {
            return limit;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("limit")) {
                try {
                    limit = Integer.parseInt(pair[1]);
                } catch (NumberFormatException ignored) {
                    limit = 50;
                }
            }
        }
        if (limit <= 0 || limit > 500) {
            limit = 50;
        }
        return limit;
    }
}
