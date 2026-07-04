package com.nextdigital.oncall.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.telegram.TelegramLinkRequest;
import com.nextdigital.oncall.dto.telegram.TelegramLinkResponse;
import com.nextdigital.oncall.telegram.service.TelegramBotServiceImpl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram", description = "Vinculación de cuentas de Telegram con usuarios de Oncall")
public class TelegramController {

    private final TelegramBotServiceImpl telegramBotService;

    @PostMapping("/link")
    @Operation(summary = "Vincular una cuenta de Telegram", description = """
            Vincula el chat de Telegram de un usuario con su perfil de Oncall.
            1. El usuario inicia el bot con /start y recibe un código.
            2. El usuario envía el código a este endpoint.
            3. El chat de Telegram queda vinculado al perfil del usuario.
            """)
    public ResponseEntity<ApiResponse<TelegramLinkResponse>> linkTelegram(
            @Valid @RequestBody TelegramLinkRequest request) {
        var result = telegramBotService.linkTelegramAccount(request.code());
        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        return ResponseEntity.badRequest().body(ApiResponse.success(result));
    }

    @GetMapping("/link-status")
    @Operation(summary = "Consultar estado de un código de vinculación")
    public ResponseEntity<ApiResponse<TelegramLinkResponse>> getLinkStatus(
            @RequestParam String code) {
        var result = telegramBotService.getLinkStatus(code);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
