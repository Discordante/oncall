-- V4__add_telegram_invite_link.sql
-- Adds invite link column to on_call_groups for sharing Telegram group access.

ALTER TABLE on_call_groups
    ADD COLUMN telegram_group_invite_link VARCHAR(255);

-- ============================================================
-- rollback
-- ============================================================
-- ALTER TABLE on_call_groups DROP COLUMN IF EXISTS telegram_group_invite_link;
