package com.nextdigital.oncall.dto.telegram;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta al intento de vinculación de Telegram")
public record TelegramLinkResponse(

        @Schema(description = "Indica si la vinculación fue exitosa", example = "true")
        boolean success,

        @Schema(description = "Mensaje descriptivo del resultado", example = "Cuenta de Telegram vinculada correctamente")
        String message
) {
}
