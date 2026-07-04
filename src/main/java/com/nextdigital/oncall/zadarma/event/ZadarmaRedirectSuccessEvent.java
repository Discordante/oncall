package com.nextdigital.oncall.zadarma.event;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Published when a Zadarma call redirection succeeds at shift transition time.
 * Triggers Telegram notification to the on-call group.
 */
public record ZadarmaRedirectSuccessEvent(
        UUID shiftId,
        UUID groupId,
        String groupName,
        UUID personId,
        String personName,
        String phoneNumber,
        OffsetDateTime startTime,
        OffsetDateTime endTime) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Madrid"));

    public String startTimeFormatted() {
        return FORMATTER.format(startTime);
    }

    public String endTimeFormatted() {
        return FORMATTER.format(endTime);
    }

    public String phoneMasked() {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return "***";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
