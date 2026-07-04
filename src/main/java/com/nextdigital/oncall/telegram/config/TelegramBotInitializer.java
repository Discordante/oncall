package com.nextdigital.oncall.telegram.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.nextdigital.oncall.telegram.service.TelegramBotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotInitializer implements ApplicationRunner {

    private final TelegramBotService telegramBotService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing Telegram bots for enabled on-call groups...");
        telegramBotService.initializeAllBots();
    }
}
