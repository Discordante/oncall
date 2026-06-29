package com.nextdigital.oncall.zadarma.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nextdigital.oncall.telegram.service.TelegramBotServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for Zadarma redirection events and sends Telegram notifications
 * to the affected on-call group's chat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZadarmaRedirectAlertListener {

    private final TelegramBotServiceImpl botService;

    @EventListener
    public void onRedirectSuccess(ZadarmaRedirectSuccessEvent event) {
        log.info("Zadarma redirect OK: shift {} → {} (group '{}')",
                event.shiftId(), event.personName(), event.groupName());

        var message = """
                📞 *Oncall redirigido a %s*

                📋 Grupo: *%s*
                📱 Teléfono: `%s`
                📆 Desde: %s
                📆 Hasta: %s
                """.formatted(
                event.personName(),
                event.groupName(),
                event.phoneMasked(),
                event.startTimeFormatted(),
                event.endTimeFormatted());

        botService.notifyGroup(event.groupId(), message);
    }

    @EventListener
    public void onRedirectFailed(ZadarmaRedirectFailedEvent event) {
        log.warn("Zadarma redirect failed for shift {}: {} (retry {}/{})",
                event.shiftId(), event.errorMessage(), event.retryCount(), event.maxRetries());

        var message = """
                ⚠️ *Fallo en la redirección de llamadas*

                📋 Grupo: *%s*
                👤 Persona de guardia: *%s*
                📞 Teléfono: `%s`
                ❌ Error: %s
                🔄 Reintento: %d/%d
                """.formatted(
                event.groupName(),
                event.personName(),
                event.phoneNumber() != null ? event.phoneNumber() : "N/A",
                event.errorMessage(),
                event.retryCount(),
                event.maxRetries());

        botService.notifyGroup(event.groupId(), message);
    }
}
