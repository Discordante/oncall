-- V11__drop_audit_ip_user_agent.sql
-- Simplificación de la auditoría: se dejan de registrar la IP y el
-- User-Agent de la petición. Ver docs/adr/003-auditoria-registro-de-acciones.md

ALTER TABLE audit_logs DROP COLUMN IF EXISTS ip_address;
ALTER TABLE audit_logs DROP COLUMN IF EXISTS user_agent;

-- ============================================================
-- rollback
-- ============================================================
-- ALTER TABLE audit_logs ADD COLUMN ip_address VARCHAR(45);
-- ALTER TABLE audit_logs ADD COLUMN user_agent VARCHAR(500);
