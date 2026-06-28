package com.nextdigital.oncall.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta con los datos de un grupo de guardia")
public record OnCallGroupResponse(
        @Schema(description = "Identificador único del grupo de guardia", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Nombre del grupo de guardia", example = "Guardia-Sistemas-Q1")
        String name,

        @Schema(description = "Precio bruto por día de guardia", example = "150.00")
        BigDecimal grossPricePerDay,

        @Schema(description = "ID del chat/grupo de Telegram donde opera el bot", example = "-1001234567890")
        String telegramGroupChatId,

        @Schema(description = "Indica si la integración con Telegram está habilitada", example = "true")
        boolean telegramBotEnabled,

        @Schema(description = "Aplicaciones asignadas al grupo")
        Set<ApplicationResponse> applications,

        @Schema(description = "Personas asignadas al grupo")
        Set<PersonResponse> people,

        @Schema(description = "Fecha y hora de creación", example = "2026-06-27T10:30:00Z")
        OffsetDateTime createdAt,

        @Schema(description = "Fecha y hora de última actualización", example = "2026-06-27T10:30:00Z")
        OffsetDateTime updatedAt
) {
}
