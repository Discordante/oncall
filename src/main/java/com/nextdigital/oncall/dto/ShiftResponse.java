package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta con los datos de un turno de guardia")
public record ShiftResponse(
        @Schema(description = "Identificador único del turno") UUID id,

        @Schema(description = "ID del grupo de guardia") UUID onCallGroupId,

        @Schema(description = "Nombre del grupo de guardia") String onCallGroupName,

        @Schema(description = "ID de la persona") UUID personId,

        @Schema(description = "Nombre de la persona") String personName,

        @Schema(description = "Email de la persona") String personEmail,

        @Schema(description = "Inicio del turno", example = "2026-07-10T00:00:00Z") OffsetDateTime startTime,

        @Schema(description = "Fin del turno", example = "2026-07-17T08:00:00Z") OffsetDateTime endTime,

        @Schema(description = "Fecha y hora de creación", example = "2026-06-27T10:30:00Z") OffsetDateTime createdAt,

        @Schema(description = "Fecha y hora de última actualización", example = "2026-06-27T10:30:00Z") OffsetDateTime updatedAt) {
}
