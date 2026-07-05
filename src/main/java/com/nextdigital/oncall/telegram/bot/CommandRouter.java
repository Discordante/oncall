package com.nextdigital.oncall.telegram.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.telegram.command.InfoCommand;
import com.nextdigital.oncall.telegram.command.ScheduleCommand;
import com.nextdigital.oncall.telegram.command.TelegramCommand;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Routes incoming Telegram commands to the appropriate handler.
 * Commands are in Spanish.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandRouter {

    private final InfoCommand infoCommand;
    private final ScheduleCommand scheduleCommand;
    private final PersonRepository personRepository;

    private final Map<String, TelegramCommand> handlers = new HashMap<>();

    @PostConstruct
    void registerHandlers() {
        handlers.put("/info", infoCommand);
        handlers.put("/turnos", scheduleCommand);
        log.info("Registered {} command handlers", handlers.size());
    }

    /**
     * Routes a command text to the appropriate handler.
     *
     * @param text    the full message text
     * @param groupId the on-call group ID
     * @param chatId  the Telegram chat ID
     * @param fromId  the Telegram user ID
     * @return the response text, or null if no handler matched
     */
    public String route(String text, UUID groupId, String chatId, String fromId) {
        if (text == null || text.isBlank()) {
            return null;
        }

        // Parse command and arguments: "/turnos" or "/turnos args"
        var parts = text.split("\\s+", 2);
        var command = parts[0].toLowerCase();
        var args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];

        // Only respond to commands starting with /
        if (!command.startsWith("/")) {
            return null;
        }

        // Check if user is linked
        var isLinked = personRepository.findByTelegramChatId(chatId).isPresent();
        if (!isLinked && !command.equals("/start")) {
            return "❌ No tienes tu cuenta vinculada.\n\n"
                    + "Escribe /start para obtener un código de verificación "
                    + "y vincular tu cuenta en la aplicación web.";
        }

        var handler = handlers.get(command);
        if (handler == null) {
            return "❌ Comando no reconocido: " + command + "\n"
                    + "Usa /info para ver los comandos disponibles.";
        }

        try {
            return handler.execute(args, groupId, chatId, fromId);
        } catch (Exception e) {
            log.error("Error executing command {}: {}", command, e.getMessage());
            return "❌ Ocurrió un error al procesar el comando. Intenta de nuevo.";
        }
    }
}
