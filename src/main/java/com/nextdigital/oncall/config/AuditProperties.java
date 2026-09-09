package com.nextdigital.oncall.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Políticas de auditoría configurables en application.yml bajo el prefijo
 * {@code app.audit}. Nada de esto debe estar hardcodeado en el código.
 * Ver docs/adr/003-auditoria-registro-de-acciones.md
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.audit")
public class AuditProperties {

    /** Interruptor global del motor de auditoría. */
    private boolean enabled = true;

    /**
     * Nombres de tabla excluidos de la auditoría (p. ej. telegram_pending_links).
     */
    private List<String> excludedEntities = new ArrayList<>(List.of("telegram_pending_links"));

    /** Meses de retención del histórico (decisión temporal: 12). */
    private int retentionMonths = 12;

    /** Cron de la purga de retención (por defecto diario a las 04:00). */
    private String retentionCron = "0 0 4 * * *";

    /** Reservada: auditoría de lecturas GET (sin comportamiento en v1). */
    private boolean logReads = false;
}
