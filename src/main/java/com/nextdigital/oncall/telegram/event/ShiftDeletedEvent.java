package com.nextdigital.oncall.telegram.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a shift is deleted.
 */
public record ShiftDeletedEvent(
        UUID shiftId,
        UUID groupId,
        UUID personId,
        String personName,
        String groupName,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
}
