-- =============================================================
-- V3: Add registration_number to users
-- Nullable because existing rows predate this column.
-- Application layer enforces NOT NULL on student registration.
-- =============================================================

ALTER TABLE users
    ADD COLUMN registration_number VARCHAR(100) UNIQUE;
