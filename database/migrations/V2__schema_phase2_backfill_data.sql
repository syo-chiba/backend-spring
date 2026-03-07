-- Phase 2: backfill normalized tables from legacy columns/data

-- 1) Backfill user -> participant (participant_type = USER)
INSERT INTO participants(participant_type, user_id, display_name, created_at, updated_at)
SELECT 'USER', u.id, u.username, NOW(6), NOW(6)
FROM users u
LEFT JOIN participants p
    ON p.participant_type = 'USER'
   AND p.user_id = u.id
WHERE p.id IS NULL;

-- 2) Backfill external participants from flow_steps.participant_name
INSERT INTO participants(participant_type, user_id, display_name, created_at, updated_at)
SELECT 'EXTERNAL', NULL, x.participant_name, NOW(6), NOW(6)
FROM (
    SELECT DISTINCT TRIM(fs.participant_name) AS participant_name
    FROM flow_steps fs
    WHERE fs.participant_name IS NOT NULL
      AND TRIM(fs.participant_name) <> ''
) x
LEFT JOIN participants p
    ON p.participant_type = 'EXTERNAL'
   AND p.display_name = x.participant_name
WHERE p.id IS NULL;

-- 3) Fill flow_steps.participant_id (prefer USER by username match, then EXTERNAL)
UPDATE flow_steps fs
JOIN participants p
  ON p.participant_type = 'USER'
 AND p.display_name = fs.participant_name
SET fs.participant_id = p.id
WHERE fs.participant_id IS NULL
  AND fs.participant_name IS NOT NULL
  AND TRIM(fs.participant_name) <> '';

UPDATE flow_steps fs
JOIN participants p
  ON p.participant_type = 'EXTERNAL'
 AND p.display_name = fs.participant_name
SET fs.participant_id = p.id
WHERE fs.participant_id IS NULL
  AND fs.participant_name IS NOT NULL
  AND TRIM(fs.participant_name) <> '';

-- 4) Backfill user_roles from legacy users.roles (supports comma-separated values)
WITH RECURSIVE split_roles AS (
    SELECT
        u.id AS user_id,
        TRIM(SUBSTRING_INDEX(COALESCE(u.roles, ''), ',', 1)) AS role_name,
        CASE
            WHEN INSTR(COALESCE(u.roles, ''), ',') = 0 THEN ''
            ELSE SUBSTRING(COALESCE(u.roles, ''), INSTR(COALESCE(u.roles, ''), ',') + 1)
        END AS rest
    FROM users u
    WHERE COALESCE(u.roles, '') <> ''

    UNION ALL

    SELECT
        sr.user_id,
        TRIM(SUBSTRING_INDEX(sr.rest, ',', 1)) AS role_name,
        CASE
            WHEN INSTR(sr.rest, ',') = 0 THEN ''
            ELSE SUBSTRING(sr.rest, INSTR(sr.rest, ',') + 1)
        END AS rest
    FROM split_roles sr
    WHERE sr.rest <> ''
)
INSERT INTO roles(name)
SELECT DISTINCT sr.role_name
FROM split_roles sr
LEFT JOIN roles r ON r.name = sr.role_name
WHERE sr.role_name <> ''
  AND r.id IS NULL;

WITH RECURSIVE split_roles AS (
    SELECT
        u.id AS user_id,
        TRIM(SUBSTRING_INDEX(COALESCE(u.roles, ''), ',', 1)) AS role_name,
        CASE
            WHEN INSTR(COALESCE(u.roles, ''), ',') = 0 THEN ''
            ELSE SUBSTRING(COALESCE(u.roles, ''), INSTR(COALESCE(u.roles, ''), ',') + 1)
        END AS rest
    FROM users u
    WHERE COALESCE(u.roles, '') <> ''

    UNION ALL

    SELECT
        sr.user_id,
        TRIM(SUBSTRING_INDEX(sr.rest, ',', 1)) AS role_name,
        CASE
            WHEN INSTR(sr.rest, ',') = 0 THEN ''
            ELSE SUBSTRING(sr.rest, INSTR(sr.rest, ',') + 1)
        END AS rest
    FROM split_roles sr
    WHERE sr.rest <> ''
)
INSERT INTO user_roles(user_id, role_id, created_at)
SELECT DISTINCT sr.user_id, r.id, NOW(6)
FROM split_roles sr
JOIN roles r ON r.name = sr.role_name
LEFT JOIN user_roles ur
    ON ur.user_id = sr.user_id
   AND ur.role_id = r.id
WHERE sr.role_name <> ''
  AND ur.id IS NULL;

-- 5) Backfill audit columns
UPDATE users
SET created_at = COALESCE(created_at, NOW(6)),
    updated_at = COALESCE(updated_at, NOW(6))
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE flows
SET updated_at = COALESCE(updated_at, created_at, NOW(6))
WHERE updated_at IS NULL;

UPDATE flow_steps
SET created_at = COALESCE(created_at, NOW(6)),
    updated_at = COALESCE(updated_at, NOW(6))
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE step_candidates
SET created_at = COALESCE(created_at, start_at, NOW(6)),
    updated_at = COALESCE(updated_at, NOW(6)),
    selected_at = CASE
        WHEN status = 'SELECTED' AND selected_at IS NULL THEN COALESCE(updated_at, NOW(6))
        ELSE selected_at
    END
WHERE created_at IS NULL
   OR updated_at IS NULL
   OR (status = 'SELECTED' AND selected_at IS NULL);
