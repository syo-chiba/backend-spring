-- Phase 1: additive schema changes (safe to run first)

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
);

CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_roles_user_role (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    participant_type VARCHAR(20) NOT NULL,
    user_id BIGINT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NULL,
    timezone VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_phase1_add_columns $$
CREATE PROCEDURE sp_phase1_add_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE users ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE users ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'deleted_at'
    ) THEN
        ALTER TABLE users ADD COLUMN deleted_at DATETIME(6) NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'flows' AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE flows ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'flows' AND column_name = 'deleted_at'
    ) THEN
        ALTER TABLE flows ADD COLUMN deleted_at DATETIME(6) NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'flows' AND column_name = 'version'
    ) THEN
        ALTER TABLE flows ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'flow_steps' AND column_name = 'participant_id'
    ) THEN
        ALTER TABLE flow_steps ADD COLUMN participant_id BIGINT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'flow_steps' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE flow_steps ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'flow_steps' AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE flow_steps ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'flow_steps' AND column_name = 'version'
    ) THEN
        ALTER TABLE flow_steps ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'step_candidates' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE step_candidates ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'step_candidates' AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE step_candidates ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'step_candidates' AND column_name = 'selected_at'
    ) THEN
        ALTER TABLE step_candidates ADD COLUMN selected_at DATETIME(6) NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'step_candidates' AND column_name = 'version'
    ) THEN
        ALTER TABLE step_candidates ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;
END $$

CALL sp_phase1_add_columns() $$
DROP PROCEDURE sp_phase1_add_columns $$

DELIMITER ;

INSERT INTO roles(name)
SELECT 'ROLE_USER'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');

INSERT INTO roles(name)
SELECT 'ROLE_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');
