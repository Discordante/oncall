package com.nextdigital.oncall.telegram.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.service.ShiftService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends scheduled Telegram notifications:
 * - Daily summary at 8:00 AM
 * - Weekly summary on Mondays at 9:00 AM
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramScheduledNotifier {

    private final TelegramBotServiceImpl botService;
    private final OnCallGroupRepository onCallGroupRepository;
    private final ShiftService shiftService;

    @Scheduled(cron = "0 0 8 * * ?")
    public void sendDailySummary() {
        log.debug("Sending daily Telegram summaries...");
        var groups = onCallGroupRepository.findByTelegramBotEnabledTrue();
        var today = LocalDate.now();
        var startOfDay = today.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        var endOfDay = today.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());

        for (var group : groups) {
            var shifts = shiftService.findAll(null, group.getId(), startOfDay, endOfDay);
            if (shifts.isEmpty()) {
                continue;
            }

            var sb = new StringBuilder();
            sb.append("☀️ *Resumen del día* — ").append(today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .append("\n\n");

            for (var shift : shifts) {
                sb.append("👤 *").append(shift.personName()).append("*\n");
                sb.append("   📆 ").append(shift.startTime().toLocalTime().withSecond(0))
                        .append(" → ").append(shift.endTime().toLocalTime().withSecond(0)).append("\n\n");
            }

            botService.notifyGroup(group.getId(), sb.toString());
        }
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void sendWeeklySummary() {
        log.debug("Sending weekly Telegram summaries...");
        var groups = onCallGroupRepository.findByTelegramBotEnabledTrue();
        var now = OffsetDateTime.now();
        var endOfWeek = now.plusWeeks(1);

        for (var group : groups) {
            var shifts = shiftService.findAll(null, group.getId(), now, endOfWeek);
            if (shifts.isEmpty()) {
                continue;
            }

            var sb = new StringBuilder();
            sb.append("📅 *Resumen semanal de guardias*\n\n");

            for (var shift : shifts) {
                sb.append("👤 *").append(shift.personName()).append("*\n");
                sb.append("   📆 ").append(shift.startTime().toLocalDate())
                        .append(" ").append(shift.startTime().toLocalTime().withSecond(0))
                        .append(" → ").append(shift.endTime().toLocalDate())
                        .append(" ").append(shift.endTime().toLocalTime().withSecond(0)).append("\n\n");
            }

            botService.notifyGroup(group.getId(), sb.toString());
        }
    }
}
