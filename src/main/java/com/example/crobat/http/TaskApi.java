package com.example.crobat.http;

import com.example.crobat.services.TaskService;

public final class TaskApi {
    private TaskApi() {
    }

    public static Router router(TaskService service, String jwtSecret) {
        Router router = new Router(jwtSecret);

        router.add("POST", "/tasks", 201, (tenant, params, body, exchange) -> {
            TaskInput input = Json.MAPPER.readValue(body, TaskInput.class);
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            return service.create(tenant, key, input.toTask());
        });

        router.add("GET", "/tasks", 200, (tenant, params, body, exchange) ->
                service.list(tenant));

        router.add("GET", "/tasks/{id}", 200, (tenant, params, body, exchange) ->
                service.get(tenant, params.get("id")));

        router.add("PUT", "/tasks/{id}", 200, (tenant, params, body, exchange) -> {
            TaskInput input = Json.MAPPER.readValue(body, TaskInput.class);
            return service.update(tenant, params.get("id"), input.toTask());
        });

        router.add("POST", "/tasks/{id}/pause", 200,
                (tenant, params, body, exchange) ->
                        service.pause(tenant, params.get("id")));

        router.add("POST", "/tasks/{id}/resume", 200,
                (tenant, params, body, exchange) ->
                        service.resume(tenant, params.get("id")));

        router.add("DELETE", "/tasks/{id}", 204, (tenant, params, body, exchange) -> {
            service.delete(tenant, params.get("id"));
            return null;
        });

        return router;
    }
}
