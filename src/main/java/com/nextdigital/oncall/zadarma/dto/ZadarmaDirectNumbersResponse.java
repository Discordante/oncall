package com.nextdigital.oncall.zadarma.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the Zadarma API response from {@code GET /v1/direct_numbers/}.
 * <p>
 * Returns information about the virtual (direct) numbers purchased by the user.
 * Note: the set of fields may differ depending on the number type.
 * </p>
 * <p>
 * Expected JSON shape:
 * <pre>{@code
 * {
 * "status": "success",
 * "info": [
 * {
 * "number": "442030000000",
 * "status": "on",
 * "country": "United Kingdom",
 * "description": "London",
 * "number_name": null,
 * "sip": "00001",
 * "sip_name": null,
 * "start_date": "2015-01-01 18:14:44",
 * "stop_date": "2016-02-11 18:14:40",
 * "monthly_fee": 2,
 * "currency": "USD",
 * "channels": 2,
 * "autorenew": "true",
 * "is_on_test": "false",
 * "type": "common"
 * }
 * ]
 * }
 * }</pre>
 *
 * <p>Possible values for {@code status}:</p>
 * <ul>
 * <li>{@code on} – number active</li>
 * <li>{@code parking} – deactivated (non-payment), recoverable within 7 days
 * after top-up</li>
 * <li>{@code checking} – connected and paid, pending activation</li>
 * <li>{@code checking_upload} – connected and paid, pending document
 * upload</li>
 * <li>{@code reserved_on} – reserved until payment</li>
 * <li>{@code reserved_checking} – reserved until payment, all documents
 * uploaded</li>
 * <li>{@code reserved_checking_upload} – reserved until payment, pending
 * document upload</li>
 * </ul>
 *
 * <p>Possible values for {@code type}:</p>
 * <ul>
 * <li>{@code common} – standard virtual number</li>
 * <li>{@code rufree} – Moscow free-call number</li>
 * <li>{@code order} – reserved but not yet connected</li>
 * </ul>
 *
 * @see <a href="https://zadarma.com/es/support/api/#intro">Zadarma API docs</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZadarmaDirectNumbersResponse(
        @JsonProperty("status") @Schema(description = "Estado de la respuesta de la API", example = "success") String status,

        @JsonProperty("info") @Schema(description = "Lista de números virtuales del usuario") List<DirectNumberInfo> info) {

    /**
     * Information about a single virtual (direct) number.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectNumberInfo(
            @JsonProperty("number") @Schema(description = "Número comprado por el usuario", example = "442030000000") String number,

            @JsonProperty("status") @Schema(description = "Estado del número: on, parking, checking, checking_upload, "
                    + "reserved_on, reserved_checking, reserved_checking_upload", example = "on") String status,

            @JsonProperty("country") @Schema(description = "País del número", example = "United Kingdom") String country,

            @JsonProperty("description") @Schema(description = "Descripción: ciudad o tipo", example = "London") String description,

            @JsonProperty("number_name") @Schema(description = "Nombre del número virtual definido por el usuario", example = "Soporte") String numberName,

            @JsonProperty("sip") @Schema(description = "SIP vinculado al número", example = "00001") String sip,

            @JsonProperty("sip_name") @Schema(description = "Nombre del SIP vinculado al número", example = "SIP principal") String sipName,

            @JsonProperty("start_date") @Schema(description = "Fecha de compra del número (YYYY-MM-DD HH:MM:SS)", example = "2015-01-01 18:14:44") String startDate,

            @JsonProperty("stop_date") @Schema(description = "Fecha de fin del periodo de pago (YYYY-MM-DD HH:MM:SS)", example = "2026-02-11 18:14:40") String stopDate,

            @JsonProperty("monthly_fee") @Schema(description = "Coste mensual del número", example = "2.00") BigDecimal monthlyFee,

            @JsonProperty("currency") @Schema(description = "Moneda del coste", example = "USD") String currency,

            @JsonProperty("channels") @Schema(description = "Número de líneas (canales) del número", example = "2") Integer channels,

            @JsonProperty("minutes") @Schema(description = "Duración total de llamadas entrantes en el mes actual (solo para revenue)", example = "120") Integer minutes,

            @JsonProperty("autorenew") @Schema(description = "Indica si está activada la renovación automática del número", example = "true") String autorenew,

            @JsonProperty("is_on_test") @Schema(description = "Indica si el número está en fase de pruebas", example = "false") String isOnTest,

            @JsonProperty("type") @Schema(description = "Tipo de número: common (virtual), rufree (Moscow gratuito), order (reservado sin conectar)", example = "common") String type) {
    }
}
