package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos de una incidencia")
public record IncidentResponse(
                @Schema(description = "ID de la incidencia") UUID id,
                @Schema(description = "Título") String title,
                @Schema(description = "Descripción") String description,
                @Schema(description = "ID de la aplicación afectada") UUID applicationId,
                @Schema(description = "Nombre de la aplicación afectada") String applicationName,
                @Schema(description = "¿Se abrió bridge con el proveedor?") boolean bridgeOpened,
                @Schema(description = "Fecha de inicio (auto desde llamada más temprana o manual)") OffsetDateTime startedAt,
                @Schema(description = "Fecha y hora de fin (null si sigue abierta)") OffsetDateTime endedAt,
                @Schema(description = "Duración en segundos (endedAt - startedAt), null si alguna fecha falta") Long durationSeconds,
                @Schema(description = "Número de llamadas vinculadas") int linkedCallsCount,
                @Schema(description = "Fecha de creación del registro") OffsetDateTime createdAt,
                @Schema(description = "Fecha de última actualización") OffsetDateTime updatedAt) {
}
