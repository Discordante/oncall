package com.nextdigital.oncall.zadarma.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the Zadarma API response from {@code GET /v1/sip/}.
 * <p>
 * Expected JSON shape:
 * <pre>{@code
 * {
 *   "status": "success",
 *   "sips": ["123456", "123457"]
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZadarmaSipListResponse(
        @JsonProperty("status") @Schema(description = "Estado de la respuesta", example = "success") String status,
        @JsonProperty("sips") @Schema(description = "Lista de identificadores SIP") List<String> sips) {
}
