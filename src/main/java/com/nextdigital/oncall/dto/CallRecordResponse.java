package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.List;
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
        @Schema(description = "ID de la incidencia vinculada (si existe)") UUID incidentId,
        @Schema(description = "Título de la incidencia vinculada (si existe)") String incidentTitle,
        @Schema(description = "ID de la aplicación asociada a la incidencia vinculada") UUID applicationId,
        @Schema(description = "Nombre de la aplicación asociada a la incidencia vinculada") String applicationName,
        @Schema(description = "Nombres de todas las aplicaciones vinculadas a través de incidencias") List<String> applicationNames,
        @Schema(description = "Fecha en que se registró en nuestra BD") OffsetDateTime createdAt,
        @Schema(description = "Indica si es una llamada de comprobación: el número de origen pertenece a una persona dada de alta en la aplicación", example = "false") boolean checkCall) {
}
