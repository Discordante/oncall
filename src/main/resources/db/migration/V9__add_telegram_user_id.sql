-- V9__add_telegram_user_id.sql
-- Links Telegram accounts by Telegram user id (message.from.id) instead of chat id.
-- In group chats every member shares the same chat id, so /start resolved to the
-- wrong person (the first one linked) and no real Telegram association was stored.

-- ============================================================
-- 1. Clean stale pending links (they expire in 15 min and are recreated on demand)
-- ============================================================
DELETE FROM telegram_pending_links;

-- ============================================================
-- 2. telegram_pending_links: add user_id and chat_type
-- ============================================================
ALTER TABLE telegram_pending_links
    ADD COLUMN user_id   VARCHAR(255) NOT NULL,
    ADD COLUMN chat_type VARCHAR(32);

CREATE INDEX idx_tpl_user_id ON telegram_pending_links (user_id);

-- ============================================================
-- 3. people: add telegram_user_id (unique Telegram identity)
-- ============================================================
ALTER TABLE people
    ADD COLUMN telegram_user_id VARCHAR(255) UNIQUE;

CREATE INDEX idx_people_telegram_user_id ON people (telegram_user_id);

-- ============================================================
-- 4. Data cleanup: remove group chat ids wrongly stored as person chat ids
--    (a person linked via /start inside a group got the group's chat id)
-- ============================================================
UPDATE people
SET telegram_chat_id = NULL
WHERE telegram_chat_id IN (
    SELECT group_chat_id
    FROM telegram_group_configs
    WHERE group_chat_id IS NOT NULL
);

-- ============================================================
-- rollback
-- ============================================================
-- DROP INDEX IF EXISTS idx_people_telegram_user_id;
-- ALTER TABLE people DROP COLUMN IF EXISTS telegram_user_id;
-- DROP INDEX IF EXISTS idx_tpl_user_id;
-- ALTER TABLE telegram_pending_links DROP COLUMN IF EXISTS user_id, DROP COLUMN IF EXISTS chat_type;
