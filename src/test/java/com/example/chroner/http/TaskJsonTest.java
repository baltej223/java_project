package com.example.chroner.http;

import com.example.chroner.domain.RequestSpec;
import com.example.chroner.domain.Task;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskJsonTest {
    @Test
    void neverSerializesHeadersBodyOrIdempotencyKey() throws Exception {
        Task task = new Task();
        task.setId("task-1");
        task.setName("hello");
        task.setPriority(com.example.chroner.domain.Priority.HIGH);
        task.setTimeoutMs(1000);
        task.setRequest(new RequestSpec("POST", "http://example.com/run",
                Map.of("Authorization", "Bearer super-secret"),
                "{\"password\":\"super-secret\"}"));
        task.setIdempotencyKey("idem-secret");

        String json = Json.MAPPER.writeValueAsString(task);

        assertTrue(json.contains("\"method\":\"POST\""));
        assertFalse(json.contains("Authorization"));
        assertFalse(json.contains("super-secret"));
        assertFalse(json.contains("idem-secret"));
    }
}
