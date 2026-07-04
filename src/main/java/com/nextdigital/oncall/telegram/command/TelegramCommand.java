package com.nextdigital.oncall.telegram.command;

import java.util.UUID;

/**
 * Functional interface for Telegram bot command handlers.
 */
@FunctionalInterface
public interface TelegramCommand {

    /**
     * Executes the command and returns the response text.
     *
     * @param args    the command arguments (excluding the command name)
     * @param groupId the on-call group ID where the command was sent
     * @param chatId  the Telegram chat ID where the command was sent
     * @param fromId  the Telegram user ID who sent the command
     * @return the response text to send back, or null for no response
     */
    String execute(String[] args, UUID groupId, String chatId, String fromId);
}
