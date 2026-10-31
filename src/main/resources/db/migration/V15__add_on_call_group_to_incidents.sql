-- V15__add_on_call_group_to_incidents.sql
-- Incidents belong to an on-call group so that each group only sees its own.
-- The column is nullable to keep legacy rows valid; the backfill below assigns
-- the group inherited from the linked calls when available.

ALTER TABLE incidents
    ADD COLUMN on_call_group_id UUID REFERENCES on_call_groups(id) ON DELETE SET NULL;

CREATE INDEX idx_incidents_group ON incidents (on_call_group_id);

-- Backfill: inherit the on-call group from the linked calls (if any).
-- NOTE: PostgreSQL has no MIN(uuid) aggregate, so we take the group of the
-- most recent linked call with DISTINCT ON instead.
UPDATE incidents i
SET on_call_group_id = sub.on_call_group_id
FROM (
    SELECT DISTINCT ON (icr.incident_id)
           icr.incident_id,
           zcr.on_call_group_id
    FROM incident_call_records icr
    JOIN zadarma_call_records zcr ON zcr.id = icr.zadarma_call_id
    WHERE zcr.on_call_group_id IS NOT NULL
    ORDER BY icr.incident_id, zcr.call_started_at DESC, zcr.id
) sub
WHERE sub.incident_id = i.id
  AND i.on_call_group_id IS NULL;
