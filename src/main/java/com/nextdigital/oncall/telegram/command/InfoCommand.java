package com.nextdigital.oncall.telegram.command;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class InfoCommand implements TelegramCommand {

    @Override
    public String execute(String[] args, UUID groupId, String chatId, String fromId) {
        return """
                🤖 <b>Comandos disponibles:</b>

                /info — Muestra esta ayuda
                /turnos — Muestra el calendario de guardias para las próximas 4 semanas
                /start — Vincula tu cuenta de Telegram con Oncall

                <b>Próximamente:</b>
                • /mis-turnos — Tus próximos turnos
                • Notificaciones de inicio y fin de guardia
                • Resumen semanal de guardias
                """;
    }
}
