package com.nextdigital.oncall.zadarma.dto;

import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Possible values for the {@code disposition} field in a Zadarma call record.
 *
 * @see <a href="https://zadarma.com/es/support/api/#statistics">Zadarma API docs</a>
 */
@Schema(description = "Estado de la llamada")
public enum ZadarmaCallDisposition {

    @Schema(description = "Conectada / conversación")
    ANSWERED("answered"),

    @Schema(description = "Ocupado")
    BUSY("busy"),

    @Schema(description = "Cancelada")
    CANCEL("cancel"),

    @Schema(description = "Sin respuesta")
    NO_ANSWER("no answer"),

    @Schema(description = "No se ha podido realizar")
    FAILED("failed"),

    @Schema(description = "Sin fondos, se ha superado el límite")
    NO_MONEY("no money"),

    @Schema(description = "Número no existe")
    UNALLOCATED_NUMBER("unallocated number"),

    @Schema(description = "Se ha superado el límite")
    NO_LIMIT("no limit"),

    @Schema(description = "Se ha superado el límite diario")
    NO_DAY_LIMIT("no day limit"),

    @Schema(description = "Se ha superado el límite de líneas")
    LINE_LIMIT("line limit"),

    @Schema(description = "Sin fondos, se ha superado el límite")
    NO_MONEY_NO_LIMIT("no money, no limit");

    private final String value;

    ZadarmaCallDisposition(String value) {
        this.value = value;
    }

    /**
     * Returns the raw API string value.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Resolves a raw string to the corresponding enum constant.
     *
     * @param raw the raw value from the API
     * @return the matching enum constant, or {@code null} if unknown
     */
    public static ZadarmaCallDisposition fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (var disp : values()) {
            if (disp.value.equals(raw)) {
                return disp;
            }
        }
        return null;
    }
}
