package com.nextdigital.oncall.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reporte de facturación de guardias para un grupo en un período")
public record BillingReportResponse(

        @Schema(description = "ID del grupo de guardia") UUID groupId,

        @Schema(description = "Nombre del grupo de guardia") String groupName,

        @Schema(description = "Precio bruto por día configurado actualmente en el grupo", example = "100.00") @JsonFormat(shape = Shape.STRING) BigDecimal grossPricePerDay,

        @Schema(description = "Inicio del período evaluado", example = "2026-06-01T00:00:00Z") OffsetDateTime from,

        @Schema(description = "Fin del período evaluado", example = "2026-06-30T23:59:59Z") OffsetDateTime to,

        @Schema(description = "Total de horas de guardia en el período", example = "480.0000") @JsonFormat(shape = Shape.STRING) BigDecimal totalHours,

        @Schema(description = "Importe total a facturar en el período", example = "2000.00") @JsonFormat(shape = Shape.STRING) BigDecimal totalBilling,

        @Schema(description = "Desglose por persona") List<BillingPersonDetail> details) {
}
