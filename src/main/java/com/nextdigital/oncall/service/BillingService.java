package com.nextdigital.oncall.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.nextdigital.oncall.dto.BillingReportResponse;

public interface BillingService {

    /**
     * Calcula el reporte de facturación de guardias.
     *
     * @param groupId  ID del grupo de guardia (obligatorio)
     * @param personId ID de la persona (opcional; si es null, se incluyen todas)
     * @param from     inicio del período (opcional; defecto: inicio del mes actual)
     * @param to       fin del período (opcional; defecto: final del mes actual)
     * @return reporte con desglose por persona
     */
    BillingReportResponse calculateReport(UUID groupId, UUID personId, OffsetDateTime from, OffsetDateTime to);
}
