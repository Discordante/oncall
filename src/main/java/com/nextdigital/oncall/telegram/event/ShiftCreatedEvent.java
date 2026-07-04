package com.nextdigital.oncall.telegram.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a new shift is created.
 */
public record ShiftCreatedEvent(
        UUID shiftId,
        UUID groupId,
        UUID personId,
        String personName,
        String groupName,
        OffsetDateTime startTime,
        OffsetDateTime endTime) {
}
