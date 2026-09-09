package com.nextdigital.oncall.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Página de registros de auditoría con metadatos de paginación")
public record AuditLogPageResponse(
        @Schema(description = "Registros de auditoría de la página") List<AuditLogResponse> items,

        @Schema(description = "Metadatos de paginación") PageMeta page) {

    @Schema(description = "Metadatos de paginación")
    public record PageMeta(
            @Schema(description = "Número de página (0-based)") int number,
            @Schema(description = "Tamaño de página") int size,
            @Schema(description = "Total de elementos") long totalElements,
            @Schema(description = "Total de páginas") int totalPages) {
    }
}
