package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de la generación automática de turnos de guardia")
public record GenerateShiftsResponse(
                @Schema(description = "ID del grupo de guardia") UUID onCallGroupId,
                @Schema(description = "Nombre del grupo de guardia") String onCallGroupName,
                @Schema(description = "Inicio de la ventana de cobertura generada") OffsetDateTime coverageStart,
                @Schema(description = "Fin de la ventana de cobertura generada") OffsetDateTime coverageEnd,
                @Schema(description = "Número de turnos creados") int shiftsCreated,
                @Schema(description = "Lista de turnos creados") List<ShiftResponse> createdShifts,
                @Schema(description = "Avisos (p. ej., sin personas elegibles)") List<String> warnings) {
}
