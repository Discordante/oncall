package com.nextdigital.oncall.zadarma.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic response wrapper for Zadarma API calls.
 * All Zadarma API responses contain at least a "status" field.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZadarmaResponse(
        @JsonProperty("status") String status,
        @JsonProperty("message") String message,
        @JsonProperty("sip") String sip,
        @JsonProperty("current_status") String currentStatus,
        @JsonProperty("destination") String destination,
        @JsonProperty("destination_value") String destinationValue) {

    public boolean isSuccess() {
        return "success".equals(status);
    }
}
