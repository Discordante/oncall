package com.nextdigital.oncall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Solicitud para crear o actualizar una aplicación")
public record ApplicationRequest(
        @Schema(description = "Nombre de la aplicación", example = "Monitorizacion-Prod")
        @NotBlank(message = "El nombre es obligatorio")
        String name,

        @Schema(description = "Descripción de la aplicación", example = "Monitorización de producción - entorno productivo")
        String description
) {
}
