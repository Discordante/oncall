package com.nextdigital.oncall.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Solicitud para crear o actualizar un grupo de guardia")
public record OnCallGroupRequest(
        @Schema(description = "Nombre del grupo de guardia", example = "Guardia-Sistemas-Q1")
        @NotBlank(message = "El nombre es obligatorio")
        String name,

        @Schema(description = "Precio bruto por día de guardia", example = "150.00")
        BigDecimal grossPricePerDay,

        @Schema(description = "Token del bot de Telegram para este grupo (opcional)")
        String telegramBotToken,

        @Schema(description = "ID del chat/grupo de Telegram donde opera el bot (opcional)")
        String telegramGroupChatId,

        @Schema(description = "Habilita la integración con Telegram", example = "false")
        Boolean telegramBotEnabled,

        @Schema(description = "IDs de las aplicaciones asignadas al grupo")
        Set<UUID> applicationIds,

        @Schema(description = "IDs de las personas asignadas al grupo")
        Set<UUID> personIds
) {
}
