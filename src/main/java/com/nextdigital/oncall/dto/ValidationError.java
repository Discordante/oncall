package com.nextdigital.oncall.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error de validación")
public record ValidationError(
        @Schema(description = "Código del error", example = "VALIDATION_ERROR")
        String code,

        @Schema(description = "Mensaje descriptivo del error", example = "El campo 'name' es obligatorio")
        String message,

        @Schema(description = "Campo que causó el error", example = "name")
        String field
) {
    public static ValidationError of(String code, String message) {
        return new ValidationError(code, message, null);
    }
}
