-- V13__add_incidents.sql
-- Incidents linked to N calls (0..N) and one Application.
-- started_at is auto-populated from the earliest linked call by the service layer.

CREATE TABLE incidents (
    id              UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255)                NOT NULL,
    description     TEXT,
    application_id  UUID                        NOT NULL REFERENCES applications(id) ON DELETE RESTRICT,
    bridge_opened   BOOLEAN                     NOT NULL DEFAULT FALSE,
    started_at      TIMESTAMP WITH TIME ZONE,
    ended_at        TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_incidents_app     ON incidents (application_id);
CREATE INDEX idx_incidents_started ON incidents (started_at);

CREATE TABLE incident_call_records (
    incident_id     UUID    NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    zadarma_call_id UUID    NOT NULL REFERENCES zadarma_call_records(id) ON DELETE CASCADE,
    PRIMARY KEY (incident_id, zadarma_call_id)
);

CREATE INDEX idx_incident_calls_call ON incident_call_records (zadarma_call_id);

-- rollback
-- DROP TABLE IF EXISTS incident_call_records;
-- DROP TABLE IF EXISTS incidents;
