package com.nextdigital.oncall.controller;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.BillingReportResponse;
import com.nextdigital.oncall.service.BillingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Facturación", description = "Cálculo de horas y facturación de guardias")
public class BillingController {

    private final BillingService service;

    @GetMapping
    @Operation(summary = "Calcular facturación", description = """
            Calcula el reporte de horas y facturación para un grupo de guardia
            en un período determinado. Usa el historial de precios del grupo para
            calcular el importe exacto de cada turno según el precio vigente en
            ese momento. Si no se especifica rango de fechas, se usa el mes actual.
            Si no se especifica persona, se incluyen todas las personas del grupo.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reporte calculado correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Parámetros de entrada inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grupo de guardia no encontrado")
    })
    public ResponseEntity<ApiResponse<BillingReportResponse>> calculate(
            @Parameter(description = "ID del grupo de guardia (obligatorio)") @RequestParam UUID groupId,
            @Parameter(description = "Filtrar por ID de persona (opcional)") @RequestParam(required = false) UUID personId,
            @Parameter(description = "Inicio del período (ISO 8601, opcional, defecto: inicio del mes actual)") @RequestParam(required = false) OffsetDateTime from,
            @Parameter(description = "Fin del período (ISO 8601, opcional, defecto: fin del mes actual)") @RequestParam(required = false) OffsetDateTime to) {
        var report = service.calculateReport(groupId, personId, from, to);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
