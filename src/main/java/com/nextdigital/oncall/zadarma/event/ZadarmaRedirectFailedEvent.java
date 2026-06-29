package com.nextdigital.oncall.zadarma.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a Zadarma call redirection fails at shift transition time.
 * This triggers Telegram alerting and retry scheduling.
 */
public record ZadarmaRedirectFailedEvent(
        UUID shiftId,
        UUID groupId,
        String groupName,
        UUID personId,
        String personName,
        String phoneNumber,
        String errorMessage,
        int retryCount,
        int maxRetries,
        OffsetDateTime failedAt) {
}
