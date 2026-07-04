-- V2__add_on_call_group_price_history.sql
-- Price history for on-call groups: allows tracking price changes over time.
-- Billing calculations use the price effective at the time of each shift.

-- ============================================================
-- 1. on_call_group_price_history
-- ============================================================
CREATE TABLE on_call_group_price_history (
    id                  UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    on_call_group_id    UUID                        NOT NULL REFERENCES on_call_groups(id) ON DELETE CASCADE,
    price               DECIMAL(10, 2)              NOT NULL,
    effective_from      TIMESTAMP WITH TIME ZONE    NOT NULL,
    effective_to        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ocgph_group         ON on_call_group_price_history (on_call_group_id);
CREATE INDEX idx_ocgph_effective     ON on_call_group_price_history (on_call_group_id, effective_from DESC);

-- ============================================================
-- 2. Migrate existing prices as the initial history entry
-- ============================================================
INSERT INTO on_call_group_price_history (on_call_group_id, price, effective_from, created_at)
SELECT id, gross_price_per_day, created_at, NOW()
FROM on_call_groups
WHERE gross_price_per_day IS NOT NULL;

-- ============================================================
-- rollback
-- ============================================================
-- DROP TABLE IF EXISTS on_call_group_price_history;
