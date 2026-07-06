package com.nextdigital.oncall.zadarma.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Disposition de una llamada entrante en Zadarma.
 *
 * @see <a href="https://zadarma.com/es/support/api/#statistics">Zadarma API docs</a>
 */
@Schema(description = "Estado de la llamada")
public enum Disposition {

    @JsonProperty("answered")
    @Schema(description = "conversación")
    ANSWERED,

    @JsonProperty("busy")
    @Schema(description = "ocupado")
    BUSY,

    @JsonProperty("cancel")
    @Schema(description = "cancelado")
    CANCEL,

    @JsonProperty("no answer")
    @Schema(description = "sin respuesta")
    NO_ANSWER,

    @JsonProperty("failed")
    @Schema(description = "no se ha podido realizar")
    FAILED,

    @JsonProperty("no money")
    @Schema(description = "sin fondos, se ha superado el límite")
    NO_MONEY,

    @JsonProperty("unallocated number")
    @Schema(description = "número no existe")
    UNALLOCATED_NUMBER,

    @JsonProperty("no limit")
    @Schema(description = "se ha superado el límite")
    NO_LIMIT,

    @JsonProperty("no day limit")
    @Schema(description = "se ha superado el límite diario")
    NO_DAY_LIMIT,

    @JsonProperty("line limit")
    @Schema(description = "se ha superado el límite de líneas")
    LINE_LIMIT,

    @JsonProperty("no money, no limit")
    @Schema(description = "se ha superado el límite; sin fondos")
    NO_MONEY_NO_LIMIT;
}
