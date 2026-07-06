package com.nextdigital.oncall.zadarma.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the Zadarma API response from {@code GET /v1/sip/}.
 * <p>
 * Actual JSON shape:
 * <pre>{@code
 * {
 * "status": "success",
 * "sips": [
 * {"id": "00001", "display_name": "SIP 1", "lines": 3},
 * {"id": "00002", "display_name": "SIP 2", "lines": 3}
 * ],
 * "left": 3
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZadarmaSipListResponse(
                @JsonProperty("status") @Schema(description = "Estado de la respuesta", example = "success") String status,
                @JsonProperty("sips") @Schema(description = "Lista de objetos SIP") List<ZadarmaSipObject> sips) {

        /**
         * Individual SIP object returned by the Zadarma /v1/sip/ endpoint.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ZadarmaSipObject(
                        @JsonProperty("id") String id,
                        @JsonProperty("display_name") String displayName,
                        @JsonProperty("lines") int lines) {
        }
}
