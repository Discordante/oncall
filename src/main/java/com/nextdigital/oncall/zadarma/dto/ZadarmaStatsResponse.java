package com.nextdigital.oncall.zadarma.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the Zadarma API response from {@code GET /v1/statistics/incoming-calls/}.
 * <p>
 * Expected JSON shape:
 * <pre>{@code
 * {
 *   "status": "success",
 *   "start": "2015-06-01 00:00:00",
 *   "end": "2015-06-29 13:45:23",
 *   "stats": [
 *     { "sip": "00001", "callstart": "2015-06-02 12:20:25", ... },
 *     { "sip": "00002", ... }
 *   ]
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZadarmaStatsResponse(
        @JsonProperty("status") @Schema(description = "Estado de la respuesta", example = "success") String status,
        @JsonProperty("start") @Schema(description = "Fecha de inicio del periodo consultado", example = "2015-06-01 00:00:00") String start,
        @JsonProperty("end") @Schema(description = "Fecha de fin del periodo consultado", example = "2015-06-29 13:45:23") String end,
        @JsonProperty("stats") @Schema(description = "Lista de registros de llamadas") List<ZadarmaCallRecord> stats) {
}
