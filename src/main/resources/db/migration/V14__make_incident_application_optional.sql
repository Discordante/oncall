-- V14__make_incident_application_optional.sql
-- Incidents may be created without an application. The user assigns the
-- affected application later while editing the incident.

ALTER TABLE incidents ALTER COLUMN application_id DROP NOT NULL;

-- rollback
-- ALTER TABLE incidents ALTER COLUMN application_id SET NOT NULL;
