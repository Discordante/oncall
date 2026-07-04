-- V1__init_core_schema.sql
-- Core schema for Oncall: applications, people, on-call groups, and shifts.

-- ============================================================
-- 1. applications
-- ============================================================
CREATE TABLE applications (
    id          UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255)                NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 2. people
-- ============================================================
CREATE TABLE people (
    id           UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255)                NOT NULL,
    email        VARCHAR(255)                NOT NULL,
    phone_number VARCHAR(50),
    is_active    BOOLEAN                     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_people_email ON people (email);

-- ============================================================
-- 3. on_call_groups
-- ============================================================
CREATE TABLE on_call_groups (
    id                  UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255)                NOT NULL,
    gross_price_per_day DECIMAL(10, 2),
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 4. Join table: on_call_groups <-> applications
-- ============================================================
CREATE TABLE on_call_group_apps (
    on_call_group_id    UUID    NOT NULL REFERENCES on_call_groups(id) ON DELETE CASCADE,
    application_id      UUID    NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    PRIMARY KEY (on_call_group_id, application_id)
);

CREATE INDEX idx_ocga_application ON on_call_group_apps (application_id);

-- ============================================================
-- 5. Join table: on_call_groups <-> people
-- ============================================================
CREATE TABLE on_call_group_people (
    on_call_group_id    UUID    NOT NULL REFERENCES on_call_groups(id) ON DELETE CASCADE,
    person_id           UUID    NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    PRIMARY KEY (on_call_group_id, person_id)
);

CREATE INDEX idx_ocgp_person ON on_call_group_people (person_id);

-- ============================================================
-- 6. shifts
-- ============================================================
CREATE TABLE shifts (
    id                  UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    on_call_group_id    UUID                        NOT NULL REFERENCES on_call_groups(id),
    person_id           UUID                        NOT NULL REFERENCES people(id),
    start_time          TIMESTAMP WITH TIME ZONE    NOT NULL,
    end_time            TIMESTAMP WITH TIME ZONE    NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_shift_time_range CHECK (start_time < end_time)
);

CREATE INDEX idx_shifts_on_call_group ON shifts (on_call_group_id);
CREATE INDEX idx_shifts_person       ON shifts (person_id);
CREATE INDEX idx_shifts_time_range   ON shifts (start_time, end_time);

-- ============================================================
-- rollback
-- ============================================================
-- DROP TABLE IF EXISTS shifts;
-- DROP TABLE IF EXISTS on_call_group_people;
-- DROP TABLE IF EXISTS on_call_group_apps;
-- DROP TABLE IF EXISTS on_call_groups;
-- DROP TABLE IF EXISTS people;
-- DROP TABLE IF EXISTS applications;
