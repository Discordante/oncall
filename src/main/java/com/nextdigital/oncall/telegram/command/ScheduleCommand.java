package com.nextdigital.oncall.telegram.command;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nextdigital.oncall.service.ShiftService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ScheduleCommand implements TelegramCommand {

    private final ShiftService shiftService;

    @Override
    public String execute(String[] args, UUID groupId, String chatId, String fromId) {
        if (groupId == null) {
            return "❌ No se pudo determinar el grupo de guardia. "
                    + "Asegúrate de enviar este comando en el chat del grupo correcto.";
        }

        var now = ZonedDateTime.now(MADRID).toOffsetDateTime();
        var fourWeeksLater = now.plusWeeks(4);

        var shifts = shiftService.findAll(null, groupId, now, fourWeeksLater);

        if (shifts.isEmpty()) {
            return "📅 No hay guardias programadas para las próximas 4 semanas.";
        }

        var sb = new StringBuilder();
        sb.append("📅 <b>Guardias — Próximas 4 semanas</b>\n\n");

        for (var shift : shifts) {
            var start = shift.startTime().atZoneSameInstant(MADRID);
            var end = shift.endTime().atZoneSameInstant(MADRID);
            var personName = shift.personName().split(" ")[0];

            sb.append("👤 <b>").append(personName).append("</b>\n");
            sb.append("   📆 ").append(formatZoned(start)).append(" → ").append(formatZoned(end)).append("\n");
            sb.append("   ⏱ ").append(durationHours(start, end)).append("h\n\n");
        }

        return sb.toString();
    }

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter DATE_TZ = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm (xxx)");

    private String formatZoned(ZonedDateTime dt) {
        return dt.format(DATE_TZ);
    }

    private long durationHours(ZonedDateTime start, ZonedDateTime end) {
        return java.time.Duration.between(start, end).toHours();
    }
}
