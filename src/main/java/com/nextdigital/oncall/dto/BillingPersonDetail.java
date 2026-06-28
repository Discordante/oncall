package com.nextdigital.oncall.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Detalle de facturación individual por persona")
public record BillingPersonDetail(

        @Schema(description = "ID de la persona") UUID personId,

        @Schema(description = "Nombre de la persona") String personName,

        @Schema(description = "Email de la persona") String personEmail,

        @Schema(description = "Total de horas de guardia en el período", example = "120.5000") @JsonFormat(shape = Shape.STRING) BigDecimal totalHours,

        @Schema(description = "Total de días de guardia (horas / 24)", example = "5.0208") @JsonFormat(shape = Shape.STRING) BigDecimal totalDays,

        @Schema(description = "Precio bruto por día aplicado", example = "100.00") @JsonFormat(shape = Shape.STRING) BigDecimal grossPricePerDay,

        @Schema(description = "Importe total a facturar (días × precio/día)", example = "502.08") @JsonFormat(shape = Shape.STRING) BigDecimal totalBilling) {
}
