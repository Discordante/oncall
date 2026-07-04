package com.nextdigital.oncall.telegram.command;

import java.time.OffsetDateTime;
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

        var now = OffsetDateTime.now();
        var fourWeeksLater = now.plusWeeks(4);

        var shifts = shiftService.findAll(null, groupId, now, fourWeeksLater);

        if (shifts.isEmpty()) {
            return "📅 No hay guardias programadas para las próximas 4 semanas.";
        }

        var sb = new StringBuilder();
        sb.append("📅 *Guardias — Próximas 4 semanas*\n\n");

        for (var shift : shifts) {
            var start = shift.startTime();
            var end = shift.endTime();
            var personName = shift.personName();

            sb.append("👤 *").append(personName).append("*\n");
            sb.append("   📆 ").append(formatDateTime(start)).append(" → ").append(formatDateTime(end)).append("\n");
            sb.append("   ⏱ ").append(durationHours(start, end)).append("h\n\n");
        }

        return sb.toString();
    }

    private String formatDateTime(OffsetDateTime dt) {
        return dt.toLocalDate() + " " + dt.toLocalTime().withSecond(0);
    }

    private long durationHours(OffsetDateTime start, OffsetDateTime end) {
        return java.time.Duration.between(start, end).toHours();
    }
}
