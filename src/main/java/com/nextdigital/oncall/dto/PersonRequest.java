package com.nextdigital.oncall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Solicitud para crear o actualizar una persona")
public record PersonRequest(
                @Schema(description = "Nombre completo de la persona", example = "Juan Pérez") @NotBlank(message = "El nombre es obligatorio") String name,

                @Schema(description = "Correo electrónico", example = "juan.perez@nextdigital.es") @NotBlank(message = "El email es obligatorio") @Email(message = "El email no tiene un formato válido") String email,

                @Schema(description = "Número de teléfono", example = "+34 612 345 678") String phoneNumber,

                @Schema(description = "Indica si la persona está activa", example = "true") Boolean isActive) {
}
