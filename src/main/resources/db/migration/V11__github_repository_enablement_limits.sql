-- =============================================================
-- V11: Repository enablement state
-- Introduces persistent enabled/disabled state per linked repository
-- to separate linked-capacity from active-enabled capacity.
-- =============================================================

ALTER TABLE project_repository_links
    ADD COLUMN is_enabled BOOLEAN;

UPDATE project_repository_links
SET is_enabled = TRUE
WHERE is_enabled IS NULL;

ALTER TABLE project_repository_links
    ALTER COLUMN is_enabled SET NOT NULL;

ALTER TABLE project_repository_links
    ALTER COLUMN is_enabled SET DEFAULT TRUE;

CREATE INDEX idx_project_repository_links_enabled
    ON project_repository_links (project_id, is_enabled, linked_at DESC);

-- Normalize existing data: ensure at most one enabled primary per project.
WITH duplicate_enabled_primaries AS (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY project_id
                   ORDER BY linked_at DESC, created_at DESC, id ASC
               ) AS rn
        FROM project_repository_links
        WHERE is_enabled = TRUE
          AND is_primary = TRUE
    ) ranked
    WHERE rn > 1
)
UPDATE project_repository_links link
SET is_primary = FALSE
WHERE link.id IN (SELECT id FROM duplicate_enabled_primaries);

-- If a project has enabled links but no enabled primary, pick the newest enabled link.
WITH first_enabled AS (
    SELECT id, project_id
    FROM (
        SELECT id,
               project_id,
               ROW_NUMBER() OVER (
                   PARTITION BY project_id
                   ORDER BY linked_at DESC, created_at DESC, id ASC
               ) AS rn
        FROM project_repository_links
        WHERE is_enabled = TRUE
    ) ranked
    WHERE rn = 1
),
missing_enabled_primary AS (
    SELECT candidate.project_id, candidate.id
    FROM first_enabled candidate
    WHERE NOT EXISTS (
        SELECT 1
        FROM project_repository_links link
        WHERE link.project_id = candidate.project_id
          AND link.is_enabled = TRUE
          AND link.is_primary = TRUE
    )
)
UPDATE project_repository_links link
SET is_primary = TRUE
WHERE link.id IN (SELECT id FROM missing_enabled_primary);

CREATE UNIQUE INDEX uk_project_repository_links_enabled_primary
    ON project_repository_links (project_id)
    WHERE is_enabled = TRUE AND is_primary = TRUE;
