package com.nextdigital.oncall.telegram.service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter DATE_TZ = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm (xxx)");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Scheduled(cron = "0 0 8 * * ?")
    public void sendDailySummary() {
        log.debug("Sending daily Telegram summaries...");
        var groups = onCallGroupRepository.findByTelegramBotEnabledTrue();
        var nowMadrid = ZonedDateTime.now(MADRID);
        var today = nowMadrid.toLocalDate();
        var startOfDay = today.atStartOfDay(MADRID).toOffsetDateTime();
        var endOfDay = today.plusDays(1).atStartOfDay(MADRID).toOffsetDateTime();

        for (var group : groups) {
            var shifts = shiftService.findAll(null, group.getId(), startOfDay, endOfDay);
            if (shifts.isEmpty()) {
                continue;
            }

            var sb = new StringBuilder();
            sb.append("☀️ <b>Resumen del día</b> — ").append(today.format(DATE_ONLY))
                    .append("\n\n");

            for (var shift : shifts) {
                var start = shift.startTime().atZoneSameInstant(MADRID);
                var end = shift.endTime().atZoneSameInstant(MADRID);
                var name = shift.personName().split(" ")[0];

                sb.append("👤 <b>").append(name).append("</b>\n");
                sb.append("   📆 ").append(start.format(DATE_TZ))
                        .append(" → ").append(end.format(DATE_TZ)).append("\n\n");
            }

            botService.notifyGroup(group.getId(), sb.toString());
        }
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void sendWeeklySummary() {
        log.debug("Sending weekly Telegram summaries...");
        var groups = onCallGroupRepository.findByTelegramBotEnabledTrue();
        var now = ZonedDateTime.now(MADRID).toOffsetDateTime();
        var endOfWeek = now.plusWeeks(1);

        for (var group : groups) {
            var shifts = shiftService.findAll(null, group.getId(), now, endOfWeek);
            if (shifts.isEmpty()) {
                continue;
            }

            var sb = new StringBuilder();
            sb.append("📅 <b>Resumen semanal de guardias</b>\n\n");

            for (var shift : shifts) {
                var start = shift.startTime().atZoneSameInstant(MADRID);
                var end = shift.endTime().atZoneSameInstant(MADRID);
                var name = shift.personName().split(" ")[0];

                sb.append("👤 <b>").append(name).append("</b>\n");
                sb.append("   📆 ").append(start.format(DATE_TZ))
                        .append(" → ").append(end.format(DATE_TZ)).append("\n\n");
            }

            botService.notifyGroup(group.getId(), sb.toString());
        }
    }

    @Scheduled(cron = "0 0 8 1 * ?")
    public void sendMonthlySummary() {
        log.debug("Sending monthly Telegram summaries...");
        var groups = onCallGroupRepository.findByTelegramBotEnabledTrue();
        var nowMadrid = ZonedDateTime.now(MADRID);
        var today = nowMadrid.toLocalDate();
        var startOfMonth = today.withDayOfMonth(1);
        var endOfMonth = today.withDayOfMonth(today.lengthOfMonth()).plusDays(1);
        var monthStart = startOfMonth.atStartOfDay(MADRID).toOffsetDateTime();
        var monthEnd = endOfMonth.atStartOfDay(MADRID).toOffsetDateTime();
        var monthLabel = today.format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        for (var group : groups) {
            var shifts = shiftService.findAll(null, group.getId(), monthStart, monthEnd);
            if (shifts.isEmpty()) {
                continue;
            }

            var sb = new StringBuilder();
            sb.append("📊 <b>Resumen mensual de guardias — ")
                    .append(monthLabel).append("</b>\n\n");

            for (var shift : shifts) {
                var start = shift.startTime().atZoneSameInstant(MADRID);
                var end = shift.endTime().atZoneSameInstant(MADRID);
                var name = shift.personName().split(" ")[0];

                sb.append("👤 <b>").append(name).append("</b>\n");
                sb.append("   📆 ").append(start.format(DATE_TZ))
                        .append(" → ").append(end.format(DATE_TZ));
                var h = Duration.between(start, end).toHours();
                sb.append(" ⏱ ").append(h).append("h\n\n");
            }

            botService.notifyGroup(group.getId(), sb.toString());
        }
    }
}
