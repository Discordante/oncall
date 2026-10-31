package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Solicitud para crear o actualizar una incidencia")
public record IncidentRequest(
                @NotBlank(message = "El título es obligatorio") @Schema(description = "Título de la incidencia", example = "Caída del servidor de producción") String title,

                @Schema(description = "ID de la aplicación afectada (opcional, se puede asignar al editar)") UUID applicationId,

                @Schema(description = "Descripción detallada de la incidencia") String description,

                @Schema(description = "Indica si se abrió bridge con el proveedor") boolean bridgeOpened,

                @Schema(description = "Fecha y hora de inicio (manual, solo cuando no se vinculan llamadas)") OffsetDateTime startedAt,

                @Schema(description = "Fecha y hora de fin (opcional, null mientras siga abierta)") OffsetDateTime endedAt,

                @Schema(description = "IDs de llamadas de Zadarma vinculadas a esta incidencia (0..N)") List<UUID> callRecordIds,

                @Schema(description = "ID del grupo de guardia al que pertenece la incidencia (opcional al actualizar: si no se envía se conserva el actual)") UUID onCallGroupId) {
}
