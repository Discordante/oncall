package com.nextdigital.oncall.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Envoltorio de respuesta estándar de la API")
public record ApiResponse<T>(
        @Schema(description = "Datos de la respuesta")
        T data,

        @Schema(description = "Lista de errores, si los hay")
        List<ValidationError> errors
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> error(List<ValidationError> errors) {
        return new ApiResponse<>(null, errors);
    }

    public static <T> ApiResponse<T> error(ValidationError error) {
        return new ApiResponse<>(null, List.of(error));
    }
}
