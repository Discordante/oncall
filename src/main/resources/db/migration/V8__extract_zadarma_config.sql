-- V8__extract_zadarma_config.sql
-- Extracts Zadarma configuration from on_call_groups into a separate 1:1 table.
-- This normalizes the schema so that Zadarma config is in its own table,
-- referenced by a single UUID FK column in on_call_groups.
-- Adds an `active` column to enable/disable call redirection independently of sipId.

-- ============================================================
-- 1. Create the zadarma_configs table
-- ============================================================
CREATE TABLE zadarma_configs (
    id                  UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    sip_id              VARCHAR(255),
    redirection_type    VARCHAR(50)                 NOT NULL DEFAULT 'sip',
    active              BOOLEAN                     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 2. Add FK column to on_call_groups (UNIQUE enforces 1:1)
-- ============================================================
ALTER TABLE on_call_groups
    ADD COLUMN zadarma_config_id UUID UNIQUE;

-- ============================================================
-- 3. Migrate existing data
--    For each on_call_group that has a zadarma_sip_id configured,
--    create a row in zadarma_configs and link it back.
-- ============================================================
INSERT INTO zadarma_configs (sip_id, redirection_type, active)
SELECT
    ogc.zadarma_sip_id,
    COALESCE(ogc.zadarma_redirection_type, 'sip'),
    TRUE
FROM on_call_groups ogc
WHERE ogc.zadarma_sip_id IS NOT NULL
  AND ogc.zadarma_sip_id <> '';

-- Link the migrated rows back to on_call_groups
UPDATE on_call_groups ogc
SET zadarma_config_id = (
    SELECT zc.id
    FROM zadarma_configs zc
    WHERE zc.sip_id = ogc.zadarma_sip_id
    LIMIT 1
)
WHERE ogc.zadarma_sip_id IS NOT NULL
  AND ogc.zadarma_sip_id <> '';

-- ============================================================
-- 4. Drop old Zadarma columns from on_call_groups
-- ============================================================
ALTER TABLE on_call_groups
    DROP COLUMN IF EXISTS zadarma_sip_id,
    DROP COLUMN IF EXISTS zadarma_redirection_type;

-- ============================================================
-- 5. Add FK constraint and index on the FK column
-- ============================================================
ALTER TABLE on_call_groups
    ADD CONSTRAINT fk_on_call_groups_zadarma_config
    FOREIGN KEY (zadarma_config_id) REFERENCES zadarma_configs(id);

CREATE INDEX idx_on_call_groups_zadarma_config_id ON on_call_groups (zadarma_config_id);

-- ============================================================
-- rollback (for reference only — Flyway does not auto-rollback)
-- ============================================================
-- ALTER TABLE on_call_groups
--     ADD COLUMN zadarma_sip_id        VARCHAR(255),
--     ADD COLUMN zadarma_redirection_type VARCHAR(50);
--
-- UPDATE on_call_groups ogc
-- SET
--     zadarma_sip_id         = zc.sip_id,
--     zadarma_redirection_type = zc.redirection_type
-- FROM zadarma_configs zc
-- WHERE ogc.zadarma_config_id = zc.id;
--
-- ALTER TABLE on_call_groups DROP COLUMN IF EXISTS zadarma_config_id;
-- DROP TABLE IF EXISTS zadarma_configs;
