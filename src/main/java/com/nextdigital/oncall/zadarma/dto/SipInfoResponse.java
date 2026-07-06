package com.nextdigital.oncall.zadarma.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the enriched SIP list response.
 * <p>
 * Contains the SIP ID from Zadarma and, if available,
 * the name of the on-call group that uses it.
 * </p>
 */
@Schema(description = "Información de un SIP de Zadarma")
public record SipInfoResponse(
        @Schema(description = "Identificador SIP en Zadarma", example = "123456")
        String sipId,

        @Schema(description = "Nombre del grupo de guardia que usa este SIP, o null si no está asignado",
                example = "Soporte Técnico")
        String groupName) {
}
