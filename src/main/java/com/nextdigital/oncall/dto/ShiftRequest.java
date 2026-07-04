package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.nextdigital.oncall.validation.StartBeforeEnd;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@StartBeforeEnd(message = "La fecha de inicio debe ser anterior a la fecha de fin")
@Schema(description = "Solicitud para crear o actualizar un turno de guardia")
public record ShiftRequest(
        @Schema(description = "ID del grupo de guardia", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @NotNull(message = "El grupo de guardia es obligatorio") UUID onCallGroupId,

        @Schema(description = "ID de la persona asignada al turno", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @NotNull(message = "La persona es obligatoria") UUID personId,

        @Schema(description = "Inicio del turno", example = "2026-07-10T00:00:00Z") @NotNull(message = "La fecha de inicio es obligatoria") OffsetDateTime startTime,

        @Schema(description = "Fin del turno", example = "2026-07-17T08:00:00Z") @NotNull(message = "La fecha de fin es obligatoria") OffsetDateTime endTime) {
}
