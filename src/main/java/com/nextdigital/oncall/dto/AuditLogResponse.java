package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

@Schema(description = "Registro de auditoría de una acción de escritura")
public record AuditLogResponse(
        @Schema(description = "Id del registro de auditoría") UUID id,

        @Schema(description = "Momento en que ocurrió la acción (ISO 8601)") OffsetDateTime occurredAt,

        @Schema(description = "Tipo de acción: CREATE, UPDATE o DELETE") AuditAction action,

        @Schema(description = "Tabla de la entidad afectada") String entityType,

        @Schema(description = "Id de la fila afectada") String entityId,

        @Schema(description = "Diff campo a campo (UPDATE) o snapshot completo (CREATE/DELETE)") JsonNode changes,

        @Schema(description = "Id de la persona que realizó la acción (null = sistema)") UUID personId,

        @Schema(description = "Email del actor en el momento de la acción") String actorEmail,

        @Schema(description = "Nombre del actor en el momento de la acción") String actorName,

        @Schema(description = "IP desde la que se realizó la acción") String ipAddress,

        @Schema(description = "Método HTTP de la petición") String httpMethod,

        @Schema(description = "Ruta de la petición") String requestPath,

        @Schema(description = "User-Agent de la petición") String userAgent) {
}
