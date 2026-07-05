-- V7__extract_telegram_group_config.sql
-- Extracts Telegram configuration from on_call_groups into a separate 1:1 table.
-- This normalizes the schema so that Telegram config is in its own table,
-- referenced by a single UUID FK column in on_call_groups.

-- ============================================================
-- 1. Create the telegram_group_configs table
-- ============================================================
CREATE TABLE telegram_group_configs (
    id                  UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_token           VARCHAR(255),
    group_chat_id       VARCHAR(255),
    bot_enabled         BOOLEAN                     NOT NULL DEFAULT FALSE,
    group_invite_link   VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 2. Add FK column to on_call_groups (UNIQUE enforces 1:1)
-- ============================================================
ALTER TABLE on_call_groups
    ADD COLUMN telegram_group_config_id UUID UNIQUE;

-- ============================================================
-- 3. Migrate existing data
--    For each on_call_group that has any Telegram config,
--    create a row in telegram_group_configs and link it.
-- ============================================================
INSERT INTO telegram_group_configs (bot_token, group_chat_id, bot_enabled, group_invite_link)
SELECT
    ogc.telegram_bot_token,
    ogc.telegram_group_chat_id,
    ogc.telegram_bot_enabled,
    ogc.telegram_group_invite_link
FROM on_call_groups ogc
WHERE ogc.telegram_bot_token IS NOT NULL
   OR ogc.telegram_group_chat_id IS NOT NULL
   OR ogc.telegram_bot_enabled = TRUE
   OR ogc.telegram_group_invite_link IS NOT NULL;

-- Link the migrated rows back to on_call_groups
-- We use a correlated subquery matching all four columns to identify the correct config row.
-- This works because we just inserted exactly one row per group with data.
UPDATE on_call_groups ogc
SET telegram_group_config_id = (
    SELECT tgc.id
    FROM telegram_group_configs tgc
    WHERE tgc.bot_token IS NOT DISTINCT FROM ogc.telegram_bot_token
      AND tgc.group_chat_id IS NOT DISTINCT FROM ogc.telegram_group_chat_id
      AND tgc.bot_enabled IS NOT DISTINCT FROM ogc.telegram_bot_enabled
      AND tgc.group_invite_link IS NOT DISTINCT FROM ogc.telegram_group_invite_link
);

-- ============================================================
-- 4. Drop old Telegram columns from on_call_groups
-- ============================================================
ALTER TABLE on_call_groups
    DROP COLUMN IF EXISTS telegram_bot_token,
    DROP COLUMN IF EXISTS telegram_group_chat_id,
    DROP COLUMN IF EXISTS telegram_bot_enabled,
    DROP COLUMN IF EXISTS telegram_group_invite_link;

-- ============================================================
-- 5. Create index on the FK column
-- ============================================================
CREATE INDEX idx_on_call_groups_tgc_id ON on_call_groups (telegram_group_config_id);

-- ============================================================
-- rollback
-- ============================================================
-- ALTER TABLE on_call_groups
--     ADD COLUMN telegram_bot_token       VARCHAR(255),
--     ADD COLUMN telegram_group_chat_id   VARCHAR(255),
--     ADD COLUMN telegram_bot_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
--     ADD COLUMN telegram_group_invite_link VARCHAR(255);
-- 
-- UPDATE on_call_groups ogc
-- SET
--     telegram_bot_token = tgc.bot_token,
--     telegram_group_chat_id = tgc.group_chat_id,
--     telegram_bot_enabled = tgc.bot_enabled,
--     telegram_group_invite_link = tgc.group_invite_link
-- FROM telegram_group_configs tgc
-- WHERE ogc.telegram_group_config_id = tgc.id;
-- 
-- DROP INDEX IF EXISTS idx_on_call_groups_tgc_id;
-- ALTER TABLE on_call_groups DROP COLUMN IF EXISTS telegram_group_config_id;
-- DROP TABLE IF EXISTS telegram_group_configs;
