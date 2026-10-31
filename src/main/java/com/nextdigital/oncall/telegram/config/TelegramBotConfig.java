package com.nextdigital.oncall.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.Getter;
import lombok.Setter;

@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "telegram.bot")
@Getter
@Setter
public class TelegramBotConfig {

    /**
     * Whether to use long polling instead of webhooks (for development).
     */
    private boolean pollingEnabled = true;

    /**
     * Polling interval in milliseconds.
     */
    private long pollingIntervalMs = 5000;

    /**
     * Public base URL for webhook registration (production).
     */
    private String webhookBaseUrl = "";

    /**
     * Master switch for outgoing notifications (shift changes, daily/weekly
     * summaries). When false, the bot still answers commands but sends no
     * notifications. Useful to silence the bot while bulk-editing shifts.
     */
    private boolean notificationsEnabled = true;
}
