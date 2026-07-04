package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta con los datos de una persona")
public record PersonResponse(
                @Schema(description = "Identificador único de la persona", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id,

                @Schema(description = "Nombre completo de la persona", example = "Juan Pérez") String name,

                @Schema(description = "Correo electrónico", example = "juan.perez@nextdigital.com") String email,

                @Schema(description = "Número de teléfono", example = "+34 612 345 678") String phoneNumber,

                @Schema(description = "Indica si la persona está activa", example = "true") Boolean isActive,

                @Schema(description = "Indica si la persona tiene una cuenta de Telegram vinculada", example = "true") boolean telegramLinked,

                @Schema(description = "Fecha y hora de creación", example = "2026-06-27T10:30:00Z") OffsetDateTime createdAt,

                @Schema(description = "Fecha y hora de última actualización", example = "2026-06-27T10:30:00Z") OffsetDateTime updatedAt) {
}
