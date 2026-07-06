package com.nextdigital.oncall.zadarma.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for a single call record inside the {@code stats} array
 * returned by the Zadarma API {@code GET /v1/statistics/incoming-calls/}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Registro individual de llamada entrante")
public record ZadarmaCallRecord(

        @JsonProperty("id")
        @Schema(description = "Identificador único de la llamada", example = "155112249")
        String id,

        @JsonProperty("sip")
        @Schema(description = "Número SIP", example = "00001")
        String sip,

        @JsonProperty("callstart")
        @Schema(description = "Fecha y hora de inicio de la llamada (YYYY-MM-DD HH:MM:SS)",
                example = "2015-06-02 12:20:25")
        String callstart,

        @JsonProperty("from")
        @Schema(description = "Número de origen (llamante)", example = "442037691880")
        long from,

        @JsonProperty("to")
        @Schema(description = "Número de destino (llamado)", example = "442037691881")
        long to,

        @JsonProperty("billseconds")
        @Schema(description = "Duración facturada en segundos", example = "0")
        int billseconds,

        @JsonProperty("disposition")
        @Schema(description = "Estado de la llamada")
        ZadarmaCallDisposition disposition,

        @JsonProperty("description")
        @Schema(description = "Descripción o ubicación asociada", example = "United Kingdom, London")
        String description) {
}
