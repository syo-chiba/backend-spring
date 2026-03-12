-- Phase 7: add per-step reservation constraint columns

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_phase7_add_step_reservation_constraints $$
CREATE PROCEDURE sp_phase7_add_step_reservation_constraints()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND column_name = 'reservable_from_date'
    ) THEN
        ALTER TABLE flow_steps
            ADD COLUMN reservable_from_date DATE NULL
            COMMENT '予約可能開始日（ステップ単位・含む）';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND column_name = 'reservable_to_date'
    ) THEN
        ALTER TABLE flow_steps
            ADD COLUMN reservable_to_date DATE NULL
            COMMENT '予約可能終了日（ステップ単位・含む）';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND column_name = 'allowed_weekdays_mask'
    ) THEN
        ALTER TABLE flow_steps
            ADD COLUMN allowed_weekdays_mask TINYINT UNSIGNED NOT NULL DEFAULT 127
            COMMENT '予約許可曜日ビットマスク（日=1 ... 土=64）';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND column_name = 'allowed_start_minute'
    ) THEN
        ALTER TABLE flow_steps
            ADD COLUMN allowed_start_minute SMALLINT UNSIGNED NOT NULL DEFAULT 0
            COMMENT '予約可能開始分（0-1439）';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND column_name = 'allowed_end_minute'
    ) THEN
        ALTER TABLE flow_steps
            ADD COLUMN allowed_end_minute SMALLINT UNSIGNED NOT NULL DEFAULT 1440
            COMMENT '予約可能終了分・排他（1-1440）';
    END IF;

    -- Normalize comments even when columns already exist.
    ALTER TABLE flow_steps
        MODIFY COLUMN reservable_from_date DATE NULL
        COMMENT '予約可能開始日（ステップ単位・含む）';
    ALTER TABLE flow_steps
        MODIFY COLUMN reservable_to_date DATE NULL
        COMMENT '予約可能終了日（ステップ単位・含む）';
    ALTER TABLE flow_steps
        MODIFY COLUMN allowed_weekdays_mask TINYINT UNSIGNED NOT NULL DEFAULT 127
        COMMENT '予約許可曜日ビットマスク（日=1 ... 土=64）';
    ALTER TABLE flow_steps
        MODIFY COLUMN allowed_start_minute SMALLINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '予約可能開始分（0-1439）';
    ALTER TABLE flow_steps
        MODIFY COLUMN allowed_end_minute SMALLINT UNSIGNED NOT NULL DEFAULT 1440
        COMMENT '予約可能終了分・排他（1-1440）';

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND index_name = 'idx_flow_steps_reservable_dates'
    ) THEN
        CREATE INDEX idx_flow_steps_reservable_dates
            ON flow_steps(reservable_from_date, reservable_to_date);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'flow_steps'
          AND index_name = 'idx_flow_steps_weekdays_time'
    ) THEN
        CREATE INDEX idx_flow_steps_weekdays_time
            ON flow_steps(allowed_weekdays_mask, allowed_start_minute, allowed_end_minute);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'flow_steps'
          AND tc.constraint_name = 'chk_flow_steps_reservable_date_range'
    ) THEN
        ALTER TABLE flow_steps
            ADD CONSTRAINT chk_flow_steps_reservable_date_range
            CHECK (
                reservable_from_date IS NULL
                OR reservable_to_date IS NULL
                OR reservable_from_date <= reservable_to_date
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'flow_steps'
          AND tc.constraint_name = 'chk_flow_steps_allowed_weekdays_mask'
    ) THEN
        ALTER TABLE flow_steps
            ADD CONSTRAINT chk_flow_steps_allowed_weekdays_mask
            CHECK (allowed_weekdays_mask BETWEEN 1 AND 127);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_schema = DATABASE()
          AND tc.table_name = 'flow_steps'
          AND tc.constraint_name = 'chk_flow_steps_allowed_time_range'
    ) THEN
        ALTER TABLE flow_steps
            ADD CONSTRAINT chk_flow_steps_allowed_time_range
            CHECK (
                allowed_start_minute BETWEEN 0 AND 1439
                AND allowed_end_minute BETWEEN 1 AND 1440
                AND allowed_start_minute < allowed_end_minute
            );
    END IF;
END $$

CALL sp_phase7_add_step_reservation_constraints() $$
DROP PROCEDURE sp_phase7_add_step_reservation_constraints $$

DELIMITER ;
