package com.nextdigital.oncall.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nextdigital.oncall.dto.GenerateShiftsResponse;
import com.nextdigital.oncall.service.ShiftGenerationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler diario que comprueba la cobertura de turnos de todos los grupos
 * y genera turnos automáticamente para aquellos que están próximos a quedarse
 * sin cobertura (último turno dentro de la ventana de margen).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oncall.shift-generation.enabled", havingValue = "true", matchIfMissing = true)
public class ShiftGenerationScheduler {

    private final ShiftGenerationService shiftGenerationService;

    @Scheduled(cron = "${oncall.shift-generation.cron:0 0 3 * * ?}")
    public void runGeneration() {
        log.info("Starting automatic shift generation check...");
        var results = shiftGenerationService.generateForAllGroups();

        long totalCreated = results.stream()
                .mapToInt(GenerateShiftsResponse::shiftsCreated)
                .sum();
        long groupsWithWarnings = results.stream()
                .filter(r -> !r.warnings().isEmpty())
                .count();

        log.info("Shift generation complete: {} groups processed, {} shifts created, {} groups with warnings",
                results.size(), totalCreated, groupsWithWarnings);

        results.stream()
                .filter(r -> !r.warnings().isEmpty())
                .forEach(r -> log.warn("Group '{}' ({}) warnings: {}",
                        r.onCallGroupName(), r.onCallGroupId(), r.warnings()));
    }
}
