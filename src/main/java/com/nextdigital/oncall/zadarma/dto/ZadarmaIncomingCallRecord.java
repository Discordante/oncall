package com.nextdigital.oncall.zadarma.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Registro individual de llamada entrante devuelto por
 * {@code GET /v1/statistics/incoming-calls/}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Registro de llamada entrante")
public record ZadarmaIncomingCallRecord(

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
        @Schema(description = "Número de teléfono de origen (quién llama)", example = "442037691880")
        String from,

        @JsonProperty("to")
        @Schema(description = "Número de teléfono de destino", example = "442037691881")
        String to,

        @JsonProperty("billseconds")
        @Schema(description = "Duración facturable en segundos", example = "0")
        int billseconds,

        @JsonProperty("disposition")
        @Schema(description = "Estado final de la llamada")
        Disposition disposition,

        @JsonProperty("description")
        @Schema(description = "Descripción o localización asociada", example = "United Kingdom, London")
        String description) {
}
