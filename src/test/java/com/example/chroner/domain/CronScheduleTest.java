package com.example.chroner.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CronScheduleTest {
    @Test
    void nextRunUsesTaskTimezone() {
        Instant after = Instant.parse("2026-01-15T07:00:00Z");
        Instant next = CronSchedule.next("0 9 * * *",
                ZoneId.of("Europe/Madrid"), after);
        assertEquals(Instant.parse("2026-01-15T08:00:00Z"), next);
    }

    @Test
    void nextRunAcrossDst() {
        Instant after = Instant.parse("2026-03-28T12:00:00Z");
        Instant next = CronSchedule.next("0 9 * * *",
                ZoneId.of("Europe/Madrid"), after);
        // 29 March 2026 is the start of summer time in Madrid: 09:00 local = 07:00Z.
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), next);
    }

    @Test
    void taskCatchUpUsesPreviousRun() {
        Task task = new Task();
        task.setName("daily");
        task.setCron("0 9 * * *");
        task.setTimezone("UTC");
        task.setRequest(new RequestSpec("POST", "http://example.com", null, ""));

        Instant previous = Instant.parse("2026-01-15T09:00:00Z");
        assertEquals(Instant.parse("2026-01-16T09:00:00Z"),
                task.nextAfter(previous));
    }

    @Test
    void rejectsBadCron() {
        assertThrows(DomainException.class,
                () -> CronSchedule.validate("this is not cron"));
    }
}
