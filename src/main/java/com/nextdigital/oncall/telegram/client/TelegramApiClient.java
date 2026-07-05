package com.nextdigital.oncall.telegram.client;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Low-level HTTP client for the Telegram Bot API.
 * Uses Spring's RestClient to communicate with
 * {@code https://api.telegram.org/bot<token>/<method>}.
 * No external Telegram library is needed.
 */
@Slf4j
@Component
public class TelegramApiClient {

    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final String PARSE_MODE_MARKDOWN = "MarkdownV2";

    private final RestClient restClient;

    public TelegramApiClient() {
        this.restClient = RestClient.builder()
                .baseUrl(API_BASE)
                .build();
    }

    /**
     * Sends a text message to a Telegram chat.
     *
     * @param botToken the bot token
     * @param chatId   the target chat ID
     * @param text     the message text
     * @return true if the message was sent successfully
     */
    public boolean sendMessage(String botToken, String chatId, String text) {
        return sendMessage(botToken, chatId, text, null);
    }

    /**
     * Sends a text message with optional parse mode.
     *
     * @param botToken  the bot token
     * @param chatId    the target chat ID
     * @param text      the message text
     * @param parseMode parse mode (HTML, MarkdownV2, or null for plain text)
     * @return true if the message was sent successfully
     */
    public boolean sendMessage(String botToken, String chatId, String text, String parseMode) {
        try {
            var payload = new SendMessagePayload(chatId, text, parseMode);
            var response = restClient.post()
                    .uri("/{token}/sendMessage", botToken)
                    .body(payload)
                    .retrieve()
                    .body(TelegramResponse.class);

            if (response != null && response.ok()) {
                log.debug("Message sent to chat {}: {} chars", chatId, text.length());
                return true;
            }

            log.warn("Failed to send message to chat {}: {}", chatId,
                    response != null ? response.description() : "no response");
            return false;
        } catch (Exception e) {
            log.error("Error sending message to chat {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    /**
     * Registers a webhook for a bot.
     *
     * @param botToken the bot token
     * @param url      the public HTTPS URL for the webhook
     * @return true if successful
     */
    public boolean setWebhook(String botToken, String url) {
        try {
            var response = restClient.post()
                    .uri("/{token}/setWebhook?url={url}", botToken, url)
                    .retrieve()
                    .body(TelegramResponse.class);

            if (response != null && response.ok()) {
                log.info("Webhook set for bot: {}", url);
                return true;
            }

            log.warn("Failed to set webhook: {}", response != null ? response.description() : "no response");
            return false;
        } catch (Exception e) {
            log.error("Error setting webhook: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Removes the webhook for a bot.
     *
     * @param botToken the bot token
     * @return true if successful
     */
    public boolean deleteWebhook(String botToken) {
        try {
            var response = restClient.post()
                    .uri("/{token}/deleteWebhook", botToken)
                    .retrieve()
                    .body(TelegramResponse.class);

            if (response != null && response.ok()) {
                log.info("Webhook deleted for bot");
                return true;
            }

            log.warn("Failed to delete webhook: {}",
                    response != null ? response.description() : "no response");
            return false;
        } catch (Exception e) {
            log.error("Error deleting webhook: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fetches updates (messages) for a bot using long polling.
     *
     * @param botToken the bot token
     * @param offset   the update ID offset (0 for all new updates)
     * @param timeout  long polling timeout in seconds
     * @return list of updates, or empty list on error
     */
    public List<TelegramUpdate> getUpdates(String botToken, long offset, int timeout) {
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{token}/getUpdates")
                            .queryParam("offset", offset)
                            .queryParam("timeout", timeout)
                            .build(botToken))
                    .retrieve()
                    .body(TelegramUpdateResponse.class);

            if (response != null && response.ok() && response.result() != null) {
                return response.result();
            }

            return List.of();
        } catch (Exception e) {
            log.error("Error fetching updates: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Creates a chat invite link for a Telegram group.
     * The bot must be an administrator in the group with "invite users" permission.
     *
     * @param botToken the bot token
     * @param chatId   the group chat ID
     * @return the invite link (e.g. "https://t.me/+AbCdEf1234"), or null if failed
     */
    public String createChatInviteLink(String botToken, String chatId) {
        try {
            var response = restClient.post()
                    .uri("/{token}/createChatInviteLink?chat_id={chatId}",
                            botToken, chatId)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && response.has("ok") && response.get("ok").asBoolean()) {
                var link = response.get("result").get("invite_link").asText();
                log.info("Chat invite link created for chat {}: {}", chatId, link);
                return link;
            }

            log.warn("Failed to create invite link for chat {}: {}",
                    chatId, response != null ? response.toString() : "no response");
            return null;
        } catch (Exception e) {
            log.warn("Could not create invite link for chat {}: {} (bot may need admin rights)",
                    chatId, e.getMessage());
            return null;
        }
    }

    /**
     * Verifies that a bot can interact with a Telegram chat by sending a chat
     * action.
     *
     * @param botToken the bot token
     * @param chatId   the chat ID to verify
     * @return true if the bot can interact with the chat
     */
    public boolean verifyBotInGroup(String botToken, String chatId) {
        try {
            var response = restClient.post()
                    .uri("/{token}/sendChatAction?chat_id={chatId}&action=typing",
                            botToken, chatId)
                    .retrieve()
                    .body(TelegramResponse.class);

            if (response != null && response.ok()) {
                log.debug("Bot verified in chat {}", chatId);
                return true;
            }

            log.warn("Bot not verified in chat {}: {}", chatId,
                    response != null ? response.description() : "no response");
            return false;
        } catch (Exception e) {
            log.warn("Bot not in chat {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    // ========== Internal DTOs for Telegram API ==========

    private record SendMessagePayload(
            @JsonProperty("chat_id") String chatId,
            String text,
            @JsonProperty("parse_mode") String parseMode) {
        SendMessagePayload(String chatId, String text, String parseMode) {
            this.chatId = chatId;
            this.text = text;
            this.parseMode = parseMode;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramResponse(
            boolean ok,
            String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramUpdateResponse(
            boolean ok,
            List<TelegramUpdate> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramUpdate(
            @JsonProperty("update_id") long updateId,
            TelegramMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramMessage(
            @JsonProperty("message_id") long messageId,
            TelegramChat chat,
            TelegramFrom from,
            String text,
            TelegramEntity[] entities) {
        public boolean isCommand() {
            if (entities != null) {
                for (var entity : entities) {
                    if ("bot_command".equals(entity.type())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramChat(
            long id,
            String type,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramFrom(
            long id,
            @JsonProperty("is_bot") boolean isBot,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String username,
            @JsonProperty("language_code") String languageCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramEntity(
            long offset,
            long length,
            String type) {
    }
}
