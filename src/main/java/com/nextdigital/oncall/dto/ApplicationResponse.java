package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta con los datos de una aplicación")
public record ApplicationResponse(
        @Schema(description = "Identificador único de la aplicación", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Nombre de la aplicación", example = "Monitorizacion-Prod")
        String name,

        @Schema(description = "Descripción de la aplicación", example = "Monitorización de producción - entorno productivo")
        String description,

        @Schema(description = "Fecha y hora de creación", example = "2026-06-27T10:30:00Z")
        OffsetDateTime createdAt,

        @Schema(description = "Fecha y hora de última actualización", example = "2026-06-27T10:30:00Z")
        OffsetDateTime updatedAt
) {
}
