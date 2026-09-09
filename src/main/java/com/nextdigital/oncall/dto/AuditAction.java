package com.nextdigital.oncall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tipo de acción registrada en la auditoría")
public enum AuditAction {

    CREATE,
    UPDATE,
    DELETE;

    public static AuditAction from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (AuditAction action : values()) {
            if (action.name().equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException(
                "Acción de auditoría no válida: '" + value + "' (valores: CREATE, UPDATE, DELETE)");
    }
}
