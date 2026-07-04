-- ============================================================
-- V6: Zadarma VoIP Call Redirection integration
-- ============================================================

-- Add Zadarma SIP configuration to on-call groups
ALTER TABLE on_call_groups
    ADD COLUMN IF NOT EXISTS zadarma_sip_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS zadarma_redirection_type VARCHAR(10) DEFAULT 'sip';

-- Add redirection tracking columns to shifts
ALTER TABLE shifts
    ADD COLUMN IF NOT EXISTS redirect_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS redirect_failed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS redirect_last_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS redirect_retry_count INT DEFAULT 0;
