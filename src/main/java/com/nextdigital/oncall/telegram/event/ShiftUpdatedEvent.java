package com.nextdigital.oncall.telegram.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a shift is updated (e.g., rescheduled, swapped).
 */
public record ShiftUpdatedEvent(
        UUID shiftId,
        UUID groupId,
        UUID personId,
        String personName,
        String groupName,
        OffsetDateTime oldStartTime,
        OffsetDateTime oldEndTime,
        OffsetDateTime newStartTime,
        OffsetDateTime newEndTime) {
}
