package com.nextdigital.oncall.zadarma.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the Zadarma API response from {@code GET /v1/sip/redirection/}.
 * <p>
 * Returns the current call forwarding (redirection) configuration for all SIP
 * numbers belonging to the account, or filtered by a specific SIP ID.
 * </p>
 * <p>
 * Expected JSON shape:
 * <pre>{@code
 * {
 *   "status": "success",
 *   "info": [
 *     {
 *       "sip_id": "00001",
 *       "status": "on",
 *       "condition": "always",
 *       "destination": "phone",
 *       "destination_value": "442037691880"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see <a href="https://zadarma.com/es/support/api/#intro">Zadarma API docs</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZadarmaSipRedirectionResponse(
        @JsonProperty("status") @Schema(description = "Estado de la respuesta", example = "success") String status,
        @JsonProperty("info") @Schema(description = "Lista de configuraciones de reenvío por SIP") List<RedirectionInfo> info) {

    /**
     * Individual SIP redirection entry.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RedirectionInfo(
            @JsonProperty("sip_id") @Schema(description = "ID del SIP del usuario", example = "00001") String sipId,
            @JsonProperty("status") @Schema(description = "Estado actual: on u off", example = "on") String status,
            @JsonProperty("condition") @Schema(description = "Condición de reenvío: always (por defecto) o unavailable",
                    example = "always") String condition,
            @JsonProperty("destination") @Schema(description = "Destino del desvío: phone o pbx",
                    example = "phone") String destination,
            @JsonProperty("destination_value") @Schema(description = "Número de desvío: teléfono o ID de centralita virtual",
                    example = "442037691880") String destinationValue) {
    }
}
