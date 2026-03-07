-- Phase 4: destructive cleanup
-- Run only after application code is fully migrated to normalized schema.
-- Preconditions:
--   1) App no longer reads users.roles
--   2) App no longer reads flow_steps.participant_name
--   3) participant_id and user_roles are fully in use

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_phase4_drop_legacy $$
CREATE PROCEDURE sp_phase4_drop_legacy()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND column_name = 'participant_name'
    ) THEN
        ALTER TABLE flow_steps DROP COLUMN participant_name;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND column_name = 'roles'
    ) THEN
        ALTER TABLE users DROP COLUMN roles;
    END IF;
END $$

CALL sp_phase4_drop_legacy() $$
DROP PROCEDURE sp_phase4_drop_legacy $$

DELIMITER ;
