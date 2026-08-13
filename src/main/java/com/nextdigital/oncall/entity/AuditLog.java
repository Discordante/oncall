package com.nextdigital.oncall.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de auditoría (append-only). Lo escribe {@code AuditEventListener}
 * dentro de la misma transacción que el cambio de negocio; nunca se modifica
 * ni se borra salvo por el job de retención.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    /** CREATE | UPDATE | DELETE (restringido por CHECK en BD). */
    @Column(nullable = false, length = 10)
    private String action;

    /** Nombre de tabla de la entidad afectada (p. ej. {@code people}). */
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    /** Id de la fila afectada (UUID como texto). */
    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    /** JSON: diff campo a campo (UPDATE) o snapshot completo (CREATE/DELETE). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String changes;

    /** Actor de la acción; NULL = acción de sistema (bot de Telegram, jobs). */
    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
