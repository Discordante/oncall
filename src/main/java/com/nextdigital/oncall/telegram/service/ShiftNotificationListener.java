package com.nextdigital.oncall.telegram.service;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nextdigital.oncall.telegram.event.ShiftCreatedEvent;
import com.nextdigital.oncall.telegram.event.ShiftDeletedEvent;
import com.nextdigital.oncall.telegram.event.ShiftUpdatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for shift lifecycle events and sends Telegram notifications
 * to the affected on-call group's chat and the involved person.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftNotificationListener {

    private final TelegramBotServiceImpl botService;

    @EventListener
    public void onShiftCreated(ShiftCreatedEvent event) {
        log.debug("Shift created event: {}", event.shiftId());

        var groupMsg = """
                🆕 *Nueva guardia asignada*
                
                👤 *%s*
                📆 %s → %s
                ⏱ %d h
                """.formatted(
                        event.personName(),
                        formatDateTime(event.startTime()),
                        formatDateTime(event.endTime()),
                        durationHours(event.startTime(), event.endTime()));

        botService.notifyGroup(event.groupId(), groupMsg);

        var personMsg = """
                🆕 *Se te ha asignado una nueva guardia*
                
                📋 Grupo: *%s*
                📆 %s → %s
                ⏱ %d h
                """.formatted(
                        event.groupName(),
                        formatDateTime(event.startTime()),
                        formatDateTime(event.endTime()),
                        durationHours(event.startTime(), event.endTime()));

        botService.notifyPerson(event.personId(), personMsg);
    }

    @EventListener
    public void onShiftUpdated(ShiftUpdatedEvent event) {
        log.debug("Shift updated event: {}", event.shiftId());

        var groupMsg = """
                🔄 *Guardia modificada*
                
                👤 *%s*
                📆 Anterior: %s → %s
                📆 Nueva: %s → %s
                """.formatted(
                        event.personName(),
                        formatDateTime(event.oldStartTime()),
                        formatDateTime(event.oldEndTime()),
                        formatDateTime(event.newStartTime()),
                        formatDateTime(event.newEndTime()));

        botService.notifyGroup(event.groupId(), groupMsg);

        var personMsg = """
                🔄 *Tu guardia ha sido modificada*
                
                📋 Grupo: *%s*
                📆 Anterior: %s → %s
                📆 Nueva: %s → %s
                """.formatted(
                        event.groupName(),
                        formatDateTime(event.oldStartTime()),
                        formatDateTime(event.oldEndTime()),
                        formatDateTime(event.newStartTime()),
                        formatDateTime(event.newEndTime()));

        botService.notifyPerson(event.personId(), personMsg);
    }

    @EventListener
    public void onShiftDeleted(ShiftDeletedEvent event) {
        log.debug("Shift deleted event: {}", event.shiftId());

        var groupMsg = """
                ❌ *Guardia cancelada*
                
                👤 *%s*
                📆 %s → %s
                """.formatted(
                        event.personName(),
                        formatDateTime(event.startTime()),
                        formatDateTime(event.endTime()));

        botService.notifyGroup(event.groupId(), groupMsg);

        var personMsg = """
                ❌ *Tu guardia ha sido cancelada*
                
                📋 Grupo: *%s*
                📆 %s → %s
                """.formatted(
                        event.groupName(),
                        formatDateTime(event.startTime()),
                        formatDateTime(event.endTime()));

        botService.notifyPerson(event.personId(), personMsg);
    }

    private String formatDateTime(OffsetDateTime dt) {
        return dt.toLocalDate() + " " + dt.toLocalTime().withSecond(0).withNano(0);
    }

    private long durationHours(OffsetDateTime start, OffsetDateTime end) {
        return Duration.between(start, end).toHours();
    }
}
