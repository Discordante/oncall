package com.nextdigital.oncall.service;

import java.util.List;
import java.util.UUID;

import com.nextdigital.oncall.dto.GenerateShiftsResponse;

/**
 * Servicio de generación automática de turnos de guardia.
 * Extiende la cobertura hacia adelante desde el turno más lejano,
 * generando turnos semanales con asignación equitativa.
 */
public interface ShiftGenerationService {

    /**
     * Genera turnos para un grupo específico desde el fin de su último turno
     * (o desde ahora + margen si no tiene turnos), cubriendo las siguientes
     * {@code coverageWeeks} semanas. Sin restricción de margen — pensado para
     * uso manual desde el controlador.
     */
    GenerateShiftsResponse generateForGroup(UUID onCallGroupId);

    /**
     * Itera todos los grupos y genera turnos solo para aquellos cuyo último
     * turno termina dentro de la ventana de margen (o no tienen turnos).
     * Pensado para el scheduler automático.
     */
    List<GenerateShiftsResponse> generateForAllGroups();
}
