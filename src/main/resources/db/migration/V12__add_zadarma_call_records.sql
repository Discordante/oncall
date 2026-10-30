-- V12__add_zadarma_call_records.sql
-- Persists incoming call records from Zadarma locally.
-- zadarma_synced_days tracks coverage so the API is only called for missing ranges.

CREATE TABLE zadarma_call_records (
    id                  UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    external_call_id    VARCHAR(64)                 NOT NULL,
    sip_id              VARCHAR(32)                 NOT NULL,
    on_call_group_id    UUID                        REFERENCES on_call_groups(id) ON DELETE SET NULL,
    caller_number       VARCHAR(32),
    destination_number  VARCHAR(32),
    call_started_at     TIMESTAMP WITH TIME ZONE    NOT NULL,
    bill_seconds        INTEGER                     NOT NULL DEFAULT 0,
    disposition         VARCHAR(50)                 NOT NULL,
    description         VARCHAR(500),
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_zadarma_external_call_id UNIQUE (external_call_id)
);

CREATE INDEX idx_zcr_sip_started  ON zadarma_call_records (sip_id, call_started_at);
CREATE INDEX idx_zcr_group        ON zadarma_call_records (on_call_group_id);
CREATE INDEX idx_zcr_call_started ON zadarma_call_records (call_started_at);

-- Tracks which (sip_id, date) combinations have been fully synced from Zadarma.
CREATE TABLE zadarma_synced_days (
    sip_id      VARCHAR(32)                 NOT NULL,
    day_date    DATE                        NOT NULL,
    synced_at   TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_zadarma_synced_days PRIMARY KEY (sip_id, day_date)
);

-- rollback
-- DROP TABLE IF EXISTS zadarma_synced_days;
-- DROP TABLE IF EXISTS zadarma_call_records;
