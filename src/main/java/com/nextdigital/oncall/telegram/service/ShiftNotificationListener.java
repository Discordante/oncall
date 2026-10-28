package com.nextdigital.oncall.telegram.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import com.nextdigital.oncall.telegram.event.ShiftCreatedEvent;
import com.nextdigital.oncall.telegram.event.ShiftDeletedEvent;
import com.nextdigital.oncall.telegram.event.ShiftUpdatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for shift lifecycle events and sends Telegram notifications
 * to the affected on-call group's chat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftNotificationListener {

    private final TelegramBotServiceImpl botService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftCreated(ShiftCreatedEvent event) {
        log.debug("Shift created event: {}", event.shiftId());

        var msg = """
                🆕 <b>Nueva guardia asignada</b>

                👤 <b>%s</b>
                📆 %s → %s
                ⏱ %d h
                """.formatted(
                firstName(event.personName()),
                formatDateTime(event.startTime()),
                formatDateTime(event.endTime()),
                durationHours(event.startTime(), event.endTime()));

        botService.notifyGroup(event.groupId(), msg);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftUpdated(ShiftUpdatedEvent event) {
        log.debug("Shift updated event: {}", event.shiftId());

        var msg = """
                🔄 <b>Guardia modificada</b>

                👤 <b>%s</b>
                📆 Anterior: %s → %s
                📆 Nueva: %s → %s
                """.formatted(
                firstName(event.personName()),
                formatDateTime(event.oldStartTime()),
                formatDateTime(event.oldEndTime()),
                formatDateTime(event.newStartTime()),
                formatDateTime(event.newEndTime()));

        botService.notifyGroup(event.groupId(), msg);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftDeleted(ShiftDeletedEvent event) {
        log.debug("Shift deleted event: {}", event.shiftId());

        var msg = """
                ❌ <b>Guardia cancelada</b>

                👤 <b>%s</b>
                📆 %s → %s
                """.formatted(
                firstName(event.personName()),
                formatDateTime(event.startTime()),
                formatDateTime(event.endTime()));

        botService.notifyGroup(event.groupId(), msg);
    }

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter DATETIME_TZ = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String formatDateTime(OffsetDateTime dt) {
        return dt.atZoneSameInstant(MADRID).format(DATETIME_TZ);
    }

    private String firstName(String fullName) {
        return fullName.split(" ")[0];
    }

    private long durationHours(OffsetDateTime start, OffsetDateTime end) {
        return Duration.between(start, end).toHours();
    }
}
