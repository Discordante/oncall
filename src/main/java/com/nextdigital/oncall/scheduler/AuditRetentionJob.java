package com.nextdigital.oncall.scheduler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.config.AuditProperties;
import com.nextdigital.oncall.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Purga periódica de la tabla de auditoría según
 * {@code app.audit.retention-months}
 * (por defecto 1 año). Es la única vía de borrado: la auditoría es append-only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionJob {

    private final AuditLogRepository repository;
    private final AuditProperties properties;

    @Scheduled(cron = "${app.audit.retention-cron:0 0 4 * * *}")
    @Transactional
    public void purgeExpiredLogs() {
        var cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(properties.getRetentionMonths());
        int deleted = repository.deleteByOccurredAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Auditoría: purgadas {} filas anteriores a {} (retención {} meses)",
                    deleted, cutoff, properties.getRetentionMonths());
        }
    }
}
