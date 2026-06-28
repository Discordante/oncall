package com.nextdigital.oncall.dto.telegram;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Solicitud para vincular una cuenta de Telegram con un usuario")
public record TelegramLinkRequest(

        @Schema(description = "Código único de verificación generado por el bot", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        @NotBlank(message = "El código de verificación es obligatorio")
        String code
) {
}
