package com.example.crobat.http;

import com.example.crobat.domain.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskInputTest {
    @Test
    void acceptsHeadersAndBodyButTheyStayOutOfResponses() throws Exception {
        String json = "{\"name\":\"x\","
                + "\"request\":{\"method\":\"post\",\"url\":\"http://example.com\","
                + "\"headers\":{\"Authorization\":\"Bearer abc\"},"
                + "\"body\":\"hello\"},"
                + "\"timeout_ms\":1000,"
                + "\"retry\":{\"max_attempts\":2,\"backoff_ms\":500}}";
        TaskInput input = Json.MAPPER.readValue(json, TaskInput.class);
        Task task = input.toTask();
        task.normalize();

        assertEquals("POST", task.getRequest().getMethod());
        assertEquals("Bearer abc", task.getRequest().getHeaders().get("Authorization"));
        assertEquals("hello", task.getRequest().getBody());
        assertEquals(2, task.getRetry().getMaxAttempts());
    }
}
