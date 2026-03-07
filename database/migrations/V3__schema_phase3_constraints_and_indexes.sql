-- Phase 3: add foreign keys, checks, and indexes

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_phase3_constraints $$
CREATE PROCEDURE sp_phase3_constraints()
BEGIN
    -- Foreign keys
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'user_roles'
          AND constraint_name = 'fk_user_roles_user'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE user_roles
            ADD CONSTRAINT fk_user_roles_user
            FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'user_roles'
          AND constraint_name = 'fk_user_roles_role'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE user_roles
            ADD CONSTRAINT fk_user_roles_role
            FOREIGN KEY (role_id) REFERENCES roles(id)
            ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'flows'
          AND constraint_name = 'fk_flows_created_by_user'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE flows
            ADD CONSTRAINT fk_flows_created_by_user
            FOREIGN KEY (created_by_user_id) REFERENCES users(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND constraint_name = 'fk_flow_steps_participant'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE flow_steps
            ADD CONSTRAINT fk_flow_steps_participant
            FOREIGN KEY (participant_id) REFERENCES participants(id)
            ON DELETE SET NULL;
    END IF;

    -- Uniques
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND constraint_name = 'uk_flow_steps_flow_id_step_order'
          AND constraint_type = 'UNIQUE'
    ) THEN
        ALTER TABLE flow_steps
            ADD CONSTRAINT uk_flow_steps_flow_id_step_order UNIQUE (flow_id, step_order);
    END IF;

    -- Checks (MySQL 8.0+ enforces CHECK)
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'flows'
          AND tc.constraint_name = 'chk_flows_status'
    ) THEN
        ALTER TABLE flows
            ADD CONSTRAINT chk_flows_status
            CHECK (status IN ('IN_PROGRESS', 'DONE', 'CANCELLED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'flow_steps'
          AND tc.constraint_name = 'chk_flow_steps_status'
    ) THEN
        ALTER TABLE flow_steps
            ADD CONSTRAINT chk_flow_steps_status
            CHECK (status IN ('PENDING', 'ACTIVE', 'CONFIRMED', 'SKIPPED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'step_candidates'
          AND tc.constraint_name = 'chk_step_candidates_status'
    ) THEN
        ALTER TABLE step_candidates
            ADD CONSTRAINT chk_step_candidates_status
            CHECK (status IN ('PROPOSED', 'SELECTED', 'REJECTED', 'EXPIRED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'participants'
          AND tc.constraint_name = 'chk_participants_type'
    ) THEN
        ALTER TABLE participants
            ADD CONSTRAINT chk_participants_type
            CHECK (participant_type IN ('USER', 'EXTERNAL'));
    END IF;

    -- Indexes
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'user_roles'
          AND index_name = 'idx_user_roles_role_id'
    ) THEN
        CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'participants'
          AND index_name = 'idx_participants_user_id'
    ) THEN
        CREATE INDEX idx_participants_user_id ON participants(user_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'participants'
          AND index_name = 'idx_participants_type_display'
    ) THEN
        CREATE INDEX idx_participants_type_display ON participants(participant_type, display_name);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flows'
          AND index_name = 'idx_flows_created_by_status_created_at'
    ) THEN
        CREATE INDEX idx_flows_created_by_status_created_at
            ON flows(created_by_user_id, status, created_at);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND index_name = 'idx_flow_steps_participant_id'
    ) THEN
        CREATE INDEX idx_flow_steps_participant_id ON flow_steps(participant_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'step_candidates'
          AND index_name = 'idx_step_candidates_step_status_start'
    ) THEN
        CREATE INDEX idx_step_candidates_step_status_start
            ON step_candidates(flow_step_id, status, start_at);
    END IF;
END $$

CALL sp_phase3_constraints() $$
DROP PROCEDURE sp_phase3_constraints $$

DELIMITER ;
