package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Registro de llamada entrante persistida localmente desde Zadarma")
public record CallRecordResponse(
        @Schema(description = "ID interno del registro en nuestra BD") UUID id,
        @Schema(description = "ID original de la llamada en Zadarma", example = "155112249") String externalCallId,
        @Schema(description = "Número SIP", example = "921495") String sipId,
        @Schema(description = "ID del grupo de guardia asociado al SIP") UUID onCallGroupId,
        @Schema(description = "Nombre del grupo de guardia") String onCallGroupName,
        @Schema(description = "Número del llamante") String callerNumber,
        @Schema(description = "Número de destino") String destinationNumber,
        @Schema(description = "Fecha y hora de inicio de la llamada") OffsetDateTime callStartedAt,
        @Schema(description = "Duración facturada en segundos") int billSeconds,
        @Schema(description = "Estado de la llamada", example = "answered") String disposition,
        @Schema(description = "Descripción o localización") String description,
        @Schema(description = "Fecha en que se registró en nuestra BD") OffsetDateTime createdAt) {
}
