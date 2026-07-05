package com.nextdigital.oncall.telegram.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.telegram.TelegramLinkResponse;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.TelegramPendingLink;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.repository.TelegramPendingLinkRepository;
import com.nextdigital.oncall.telegram.bot.CommandRouter;
import com.nextdigital.oncall.telegram.client.TelegramApiClient;
import com.nextdigital.oncall.telegram.client.TelegramApiClient.TelegramUpdate;
import com.nextdigital.oncall.telegram.config.TelegramBotConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotServiceImpl implements TelegramBotService {

    private final TelegramPendingLinkRepository pendingLinkRepository;
    private final PersonRepository personRepository;
    private final OnCallGroupRepository onCallGroupRepository;
    private final TelegramApiClient apiClient;
    private final TelegramBotConfig botConfig;
    private final CommandRouter commandRouter;

    /**
     * Map of groupId -> bot state (token, chatId, lastUpdateId).
     * Populated at startup and updated when groups are created/updated.
     */
    private final Map<UUID, BotInstance> activeBots = new ConcurrentHashMap<>();

    // ========================================================================
    // User linking
    // ========================================================================

    @Override
    @Transactional
    public TelegramLinkResponse linkTelegramAccount(String code) {
        var pendingLink = pendingLinkRepository.findByLinkCode(code).orElse(null);
        if (pendingLink == null) {
            log.warn("Invalid link code attempted: {}", code);
            return new TelegramLinkResponse(false, "Código de verificación inválido.");
        }
        if (pendingLink.isExpired()) {
            pendingLinkRepository.delete(pendingLink);
            log.warn("Expired link code: {}", code);
            return new TelegramLinkResponse(false,
                    "El código de verificación ha expirado. Solicita uno nuevo con /start.");
        }
        return new TelegramLinkResponse(false,
                "La vinculación requiere autenticación. Usa la aplicación web para completar el proceso.");
    }

    @Override
    public TelegramLinkResponse getLinkStatus(String code) {
        var pendingLink = pendingLinkRepository.findByLinkCode(code).orElse(null);
        if (pendingLink == null) {
            return new TelegramLinkResponse(false, "Código inválido o ya utilizado.");
        }
        if (pendingLink.isExpired()) {
            return new TelegramLinkResponse(false, "El código ha expirado. Solicita uno nuevo con /start.");
        }
        return new TelegramLinkResponse(true,
                "Código válido. Ingresa el código en la aplicación web para vincular tu cuenta.");
    }

    /**
     * Links a Telegram chat to a specific person. Called when the authenticated
     * user confirms the link code in the web app.
     */
    @Transactional
    public TelegramLinkResponse linkForPerson(UUID personId, String code) {
        var pendingLink = pendingLinkRepository.findByLinkCode(code).orElse(null);
        if (pendingLink == null) {
            return new TelegramLinkResponse(false, "Código de verificación inválido.");
        }
        if (pendingLink.isExpired()) {
            pendingLinkRepository.delete(pendingLink);
            return new TelegramLinkResponse(false, "El código de verificación ha expirado.");
        }

        var person = personRepository.findById(personId).orElse(null);
        if (person == null) {
            return new TelegramLinkResponse(false, "Usuario no encontrado.");
        }

        // If another person already has this chatId, unlink them first
        personRepository.findByTelegramChatId(pendingLink.getChatId())
                .ifPresent(existing -> {
                    existing.setTelegramChatId(null);
                    existing.setTelegramLinkedAt(null);
                    personRepository.save(existing);
                    log.info("Unlinked Telegram chat {} from previous person {}",
                            pendingLink.getChatId(), existing.getId());
                });

        person.setTelegramChatId(pendingLink.getChatId());
        person.setTelegramLinkedAt(OffsetDateTime.now());
        personRepository.save(person);

        pendingLinkRepository.delete(pendingLink);

        log.info("Telegram account linked: person={}, chatId={}", personId, pendingLink.getChatId());
        return new TelegramLinkResponse(true, "Cuenta de Telegram vinculada correctamente.");
    }

    // ========================================================================
    // Bot lifecycle
    // ========================================================================

    @Override
    public void registerGroupBot(UUID groupId) {
        var group = onCallGroupRepository.findById(groupId).orElse(null);
        if (group == null || group.getTelegramBotToken() == null || !group.getTelegramBotEnabled()) {
            log.warn("Cannot register bot for group {}: invalid config", groupId);
            return;
        }
        var instance = new BotInstance(
                group.getTelegramBotToken(),
                group.getTelegramGroupChatId(),
                0L);
        activeBots.put(groupId, instance);
        log.info("Bot registered for group {}: chatId={}", groupId, group.getTelegramGroupChatId());
    }

    @Override
    public void unregisterGroupBot(UUID groupId) {
        var removed = activeBots.remove(groupId);
        if (removed != null) {
            log.info("Bot unregistered for group {}", groupId);
        }
    }

    @Override
    public void initializeAllBots() {
        var groups = onCallGroupRepository.findByTelegramBotEnabledTrue();
        for (var group : groups) {
            registerGroupBot(group.getId());
        }
        log.info("Initialized {} Telegram bot(s)", activeBots.size());
    }

    /**
     * Returns the list of currently active bot group IDs.
     */
    public List<UUID> getActiveBotGroupIds() {
        return List.copyOf(activeBots.keySet());
    }

    // ========================================================================
    // Polling: fetch and process updates for all active bots
    // ========================================================================

    @Scheduled(fixedDelayString = "${telegram.bot.polling-interval-ms:5000}")
    public void pollUpdates() {
        if (!botConfig.isPollingEnabled() || activeBots.isEmpty()) {
            return;
        }

        for (var entry : activeBots.entrySet()) {
            var groupId = entry.getKey();
            var bot = entry.getValue();
            try {
                var updates = apiClient.getUpdates(bot.botToken(), bot.lastUpdateId(), 2);
                for (var update : updates) {
                    processUpdate(groupId, bot, update);
                    var newOffset = update.updateId() + 1;
                    activeBots.put(groupId, new BotInstance(
                            bot.botToken(), bot.chatId(), newOffset));
                }
            } catch (Exception e) {
                log.error("Error polling bot for group {}: {}", groupId, e.getMessage());
            }
        }
    }

    private void processUpdate(UUID groupId, BotInstance bot, TelegramUpdate update) {
        if (update.message() == null || update.message().text() == null) {
            return;
        }

        var message = update.message();
        var chatId = String.valueOf(message.chat().id());
        var text = message.text().trim();
        var fromId = String.valueOf(message.from().id());

        log.debug("Received message from {} in group chat {}: {}", fromId, chatId, text);

        // /start must be handled before the generic command router,
        // otherwise isCommand() catches it and handleStartCommand never runs
        if (text.equalsIgnoreCase("/start")) {
            handleStartCommand(bot.botToken(), chatId, fromId);
        } else if (message.isCommand()) {
            var response = commandRouter.route(text, groupId, chatId, fromId);
            if (response != null) {
                apiClient.sendMessage(bot.botToken(), chatId, response);
            }
        }
    }

    private void handleStartCommand(String botToken, String chatId, String fromId) {
        var alreadyLinked = personRepository.findByTelegramChatId(chatId);
        if (alreadyLinked.isPresent()) {
            var person = alreadyLinked.get();
            apiClient.sendMessage(botToken, chatId,
                    "✅ Ya tienes tu cuenta vinculada, " + person.getName() + ".\n"
                            + "Usa /info para ver los comandos disponibles.");
            return;
        }

        var code = UUID.randomUUID().toString().replace("-", "");
        var pendingLink = TelegramPendingLink.builder()
                .chatId(chatId)
                .linkCode(code)
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .build();
        pendingLinkRepository.save(pendingLink);

        var welcomeMessage = """
                🤖 *Bienvenido al bot de Oncall*

                Para vincular tu cuenta de Telegram con tu perfil de Oncall:

                1. Copia este código de verificación:
                `%s`

                2. Ve a la aplicación web de Oncall, a tu perfil, e ingresa el código.

                ⏰ El código expira en 15 minutos.

                Si no tienes cuenta en Oncall, contacta con tu administrador.
                """.formatted(code);

        apiClient.sendMessage(botToken, chatId, welcomeMessage);
        log.info("Generated link code for chat {}: {}", chatId, code);
    }

    // ========================================================================
    // Notifications
    // ========================================================================

    /**
     * Sends a notification message to a group's Telegram chat.
     */
    public void notifyGroup(UUID groupId, String message) {
        var bot = activeBots.get(groupId);
        if (bot == null || bot.chatId() == null) {
            log.debug("Group {} has no active bot or chatId", groupId);
            return;
        }
        apiClient.sendMessage(bot.botToken(), bot.chatId(), message);
    }

    /**
     * Sends a notification to a specific person via their linked Telegram chat.
     */
    public void notifyPerson(UUID personId, String message) {
        var person = personRepository.findById(personId).orElse(null);
        if (person == null || person.getTelegramChatId() == null) {
            log.debug("Person {} has no linked Telegram chat", personId);
            return;
        }

        for (var entry : activeBots.entrySet()) {
            var bot = entry.getValue();
            boolean sent = apiClient.sendMessage(bot.botToken(), person.getTelegramChatId(), message);
            if (sent) {
                return;
            }
        }
        log.warn("No active bot available to notify person {}", personId);
    }

    // ========================================================================
    // Invite link generation
    // ========================================================================

    @Override
    @Transactional
    public String generateGroupInviteLink(UUID groupId) {
        var group = onCallGroupRepository.findById(groupId).orElse(null);
        if (group == null) {
            log.warn("Cannot generate invite link: group {} not found", groupId);
            return null;
        }

        var botToken = group.getTelegramBotToken();
        var chatId = group.getTelegramGroupChatId();

        if (botToken == null || chatId == null) {
            log.warn("Cannot generate invite link for group {}: bot not fully configured", groupId);
            return null;
        }

        // First verify the bot is in the group
        if (!apiClient.verifyBotInGroup(botToken, chatId)) {
            log.warn("Cannot generate invite link for group {}: bot not in group or not admin", groupId);
            return null;
        }

        // Generate the invite link
        var inviteLink = apiClient.createChatInviteLink(botToken, chatId);
        if (inviteLink != null) {
            group.setTelegramGroupInviteLink(inviteLink);
            onCallGroupRepository.save(group);
            log.info("Invite link generated for group {}: {}", groupId, inviteLink);
        }

        return inviteLink;
    }

    // ========================================================================
    // Internal state
    // ========================================================================

    private record BotInstance(
            String botToken,
            String chatId,
            long lastUpdateId) {
    }
}
