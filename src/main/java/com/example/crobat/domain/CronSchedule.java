package com.example.crobat.domain;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/** Small wrapper around cron-utils so the rest of the code does not import it. */
public final class CronSchedule {
    private static final CronDefinition DEFINITION =
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
    private static final CronParser PARSER = new CronParser(DEFINITION);

    private CronSchedule() {
    }

    public static void validate(String expression) {
        try {
            PARSER.parse(expression).validate();
        } catch (RuntimeException ex) {
            throw new DomainException("bad cron expression: " + ex.getMessage());
        }
    }

    public static Instant next(String expression, ZoneId zone, Instant after) {
        try {
            Cron cron = PARSER.parse(expression);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            ZonedDateTime from = after.atZone(zone);
            Optional<ZonedDateTime> next = executionTime.nextExecution(from);
            return next.map(ZonedDateTime::toInstant).orElse(null);
        } catch (RuntimeException ex) {
            throw new DomainException("bad cron expression: " + ex.getMessage());
        }
    }
}
