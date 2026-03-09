-- Phase 5: add template schema for reusable flow definitions

CREATE TABLE IF NOT EXISTS flow_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    duration_minutes INT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    last_used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS flow_template_steps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    participant_id BIGINT NULL,
    participant_type_snapshot VARCHAR(20) NOT NULL,
    participant_name_snapshot VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_phase5_template_schema $$
CREATE PROCEDURE sp_phase5_template_schema()
BEGIN
    -- flows: source template tracking
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'flows'
          AND column_name = 'source_template_id'
    ) THEN
        ALTER TABLE flows
            ADD COLUMN source_template_id BIGINT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'flows'
          AND column_name = 'source_template_name_snapshot'
    ) THEN
        ALTER TABLE flows
            ADD COLUMN source_template_name_snapshot VARCHAR(100) NULL;
    END IF;

    -- foreign keys
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_templates'
          AND constraint_name = 'fk_flow_templates_created_by_user'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE flow_templates
            ADD CONSTRAINT fk_flow_templates_created_by_user
            FOREIGN KEY (created_by_user_id) REFERENCES users(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_template_steps'
          AND constraint_name = 'fk_flow_template_steps_template'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE flow_template_steps
            ADD CONSTRAINT fk_flow_template_steps_template
            FOREIGN KEY (template_id) REFERENCES flow_templates(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_template_steps'
          AND constraint_name = 'fk_flow_template_steps_participant'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE flow_template_steps
            ADD CONSTRAINT fk_flow_template_steps_participant
            FOREIGN KEY (participant_id) REFERENCES participants(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'flows'
          AND constraint_name = 'fk_flows_source_template'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE flows
            ADD CONSTRAINT fk_flows_source_template
            FOREIGN KEY (source_template_id) REFERENCES flow_templates(id)
            ON DELETE SET NULL;
    END IF;

    -- uniques
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_template_steps'
          AND constraint_name = 'uk_flow_template_steps_template_step_order'
          AND constraint_type = 'UNIQUE'
    ) THEN
        ALTER TABLE flow_template_steps
            ADD CONSTRAINT uk_flow_template_steps_template_step_order
            UNIQUE (template_id, step_order);
    END IF;

    -- checks
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'flow_templates'
          AND tc.constraint_name = 'chk_flow_templates_visibility'
    ) THEN
        ALTER TABLE flow_templates
            ADD CONSTRAINT chk_flow_templates_visibility
            CHECK (visibility IN ('PRIVATE', 'SHARED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'flow_template_steps'
          AND tc.constraint_name = 'chk_flow_template_steps_participant_type_snapshot'
    ) THEN
        ALTER TABLE flow_template_steps
            ADD CONSTRAINT chk_flow_template_steps_participant_type_snapshot
            CHECK (participant_type_snapshot IN ('USER', 'EXTERNAL'));
    END IF;

    -- indexes
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_templates'
          AND index_name = 'idx_flow_templates_created_by_active_updated'
    ) THEN
        CREATE INDEX idx_flow_templates_created_by_active_updated
            ON flow_templates(created_by_user_id, is_active, updated_at);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_templates'
          AND index_name = 'idx_flow_templates_visibility_active_updated'
    ) THEN
        CREATE INDEX idx_flow_templates_visibility_active_updated
            ON flow_templates(visibility, is_active, updated_at);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_template_steps'
          AND index_name = 'idx_flow_template_steps_template_id'
    ) THEN
        CREATE INDEX idx_flow_template_steps_template_id
            ON flow_template_steps(template_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_template_steps'
          AND index_name = 'idx_flow_template_steps_participant_id'
    ) THEN
        CREATE INDEX idx_flow_template_steps_participant_id
            ON flow_template_steps(participant_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flows'
          AND index_name = 'idx_flows_source_template_id'
    ) THEN
        CREATE INDEX idx_flows_source_template_id
            ON flows(source_template_id);
    END IF;
END $$

CALL sp_phase5_template_schema() $$
DROP PROCEDURE sp_phase5_template_schema $$

DELIMITER ;
