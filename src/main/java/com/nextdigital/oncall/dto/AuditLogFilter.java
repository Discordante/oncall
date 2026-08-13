package com.nextdigital.oncall.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros combinables de la consulta de auditoría. Cualquier campo a null
 * significa "sin filtro".
 */
public record AuditLogFilter(
        AuditAction action,
        String entityType,
        String entityId,
        UUID personId,
        OffsetDateTime from,
        OffsetDateTime to,
        String q) {
}
