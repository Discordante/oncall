-- V3__add_telegram_integration.sql
-- Telegram bot integration: optional per on-call group, user linking via unique codes.

-- ============================================================
-- 1. Add Telegram fields to people
-- ============================================================
ALTER TABLE people
    ADD COLUMN telegram_chat_id   VARCHAR(255) UNIQUE,
    ADD COLUMN telegram_link_code VARCHAR(36)  UNIQUE,
    ADD COLUMN telegram_linked_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_people_telegram_chat_id   ON people (telegram_chat_id);
CREATE INDEX idx_people_telegram_link_code ON people (telegram_link_code);

-- ============================================================
-- 2. Add Telegram fields to on_call_groups
-- ============================================================
ALTER TABLE on_call_groups
    ADD COLUMN telegram_bot_token      VARCHAR(255),
    ADD COLUMN telegram_group_chat_id  VARCHAR(255),
    ADD COLUMN telegram_bot_enabled    BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- 3. Pending links table: temporary codes for user-bot linking
-- ============================================================
CREATE TABLE telegram_pending_links (
    id          UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id     VARCHAR(255)                NOT NULL,
    link_code   VARCHAR(36)                 NOT NULL UNIQUE,
    created_at  TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMP WITH TIME ZONE    NOT NULL
);

CREATE INDEX idx_tpl_link_code ON telegram_pending_links (link_code);
CREATE INDEX idx_tpl_chat_id   ON telegram_pending_links (chat_id);

-- ============================================================
-- rollback
-- ============================================================
-- DROP TABLE IF EXISTS telegram_pending_links;
-- ALTER TABLE on_call_groups DROP COLUMN IF EXISTS telegram_bot_enabled;
-- ALTER TABLE on_call_groups DROP COLUMN IF EXISTS telegram_group_chat_id;
-- ALTER TABLE on_call_groups DROP COLUMN IF EXISTS telegram_bot_token;
-- ALTER TABLE people DROP COLUMN IF EXISTS telegram_linked_at;
-- ALTER TABLE people DROP COLUMN IF EXISTS telegram_link_code;
-- ALTER TABLE people DROP COLUMN IF EXISTS telegram_chat_id;
