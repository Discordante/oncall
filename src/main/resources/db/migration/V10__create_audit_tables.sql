-- V10__create_audit_tables.sql
-- Auditoría de acciones: tabla única append-only donde Hibernate registra
-- cada INSERT/UPDATE/DELETE con su autor (actor desde el JWT de Google).
-- Ver docs/adr/003-auditoria-registro-de-acciones.md

-- ============================================================
-- 1. audit_logs
-- ============================================================
-- Desviación documentada de la convención de columnas obligatorias:
-- tabla append-only, no lleva updated_at (nada se actualiza jamás).
CREATE TABLE audit_logs (
    id           UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at  TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    action       VARCHAR(10)                 NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE')),
    entity_type  VARCHAR(255)                NOT NULL,
    entity_id    VARCHAR(64)                 NOT NULL,
    changes      JSONB,
    person_id    UUID                        REFERENCES people(id),
    actor_email  VARCHAR(255),
    actor_name   VARCHAR(255),
    ip_address   VARCHAR(45),
    http_method  VARCHAR(10),
    request_path VARCHAR(500),
    user_agent   VARCHAR(500)
);

CREATE INDEX idx_audit_logs_entity      ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_person      ON audit_logs (person_id);
CREATE INDEX idx_audit_logs_changes     ON audit_logs USING GIN (changes);

-- ============================================================
-- rollback
-- ============================================================
-- DROP TABLE IF EXISTS audit_logs;
