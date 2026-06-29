package com.nextdigital.oncall.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for Zadarma VoIP API integration.
 * Follows the same pattern as
 * {@link com.nextdigital.oncall.telegram.config.TelegramBotConfig}.
 */
@Configuration
@ConfigurationProperties(prefix = "zadarma")
@Getter
@Setter
public class ZadarmaConfig {

    private Api api = new Api();
    private Redirect redirect = new Redirect();

    @Getter
    @Setter
    public static class Api {
        private String key;
        private String secret;
        private String baseUrl = "https://api.zadarma.com";
    }

    @Getter
    @Setter
    public static class Redirect {
        private int retryMaxAttempts = 10;
        private int retryInitialDelaySeconds = 30;
        private int fallbackCheckIntervalSeconds = 300;
    }
}
