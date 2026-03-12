-- Phase 6: fill logical names into MySQL comments
-- MySQL Workbench / mysql client compatible
-- Updates only: empty comment or "[TODO] ..."

SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_fill_logical_comments $$
CREATE PROCEDURE sp_fill_logical_comments(IN p_schema VARCHAR(64), IN p_force BOOLEAN)
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_table_name VARCHAR(64);
    DECLARE v_column_name VARCHAR(64);
    DECLARE v_column_type TEXT;
    DECLARE v_is_nullable VARCHAR(3);
    DECLARE v_column_default TEXT;
    DECLARE v_extra TEXT;
    DECLARE v_character_set_name VARCHAR(64);
    DECLARE v_collation_name VARCHAR(64);
    DECLARE v_comment TEXT;
    DECLARE v_definition LONGTEXT;

    DECLARE c_tables CURSOR FOR
        SELECT t.table_name
        FROM information_schema.tables t
        WHERE t.table_schema = p_schema
          AND t.table_type = 'BASE TABLE'
          AND (
              p_force = TRUE
              OR IFNULL(TRIM(t.table_comment), '') = ''
              OR IFNULL(TRIM(t.table_comment), '') LIKE '[TODO] %'
          )
        ORDER BY t.table_name;

    DECLARE c_columns CURSOR FOR
        SELECT
            c.table_name,
            c.column_name,
            c.column_type,
            c.is_nullable,
            c.column_default,
            c.extra,
            c.character_set_name,
            c.collation_name
        FROM information_schema.columns c
        WHERE c.table_schema = p_schema
          AND (
              p_force = TRUE
              OR IFNULL(TRIM(c.column_comment), '') = ''
              OR IFNULL(TRIM(c.column_comment), '') LIKE '[TODO] %'
          )
        ORDER BY c.table_name, c.ordinal_position;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    SET @schema_name = p_schema;

    -- table comments
    SET done = 0;
    OPEN c_tables;
    loop_tables: LOOP
        FETCH c_tables INTO v_table_name;
        IF done = 1 THEN
            LEAVE loop_tables;
        END IF;

        SET v_comment = CASE v_table_name
            WHEN 'users' THEN 'ユーザー'
            WHEN 'roles' THEN 'ロール'
            WHEN 'user_roles' THEN 'ユーザーロール紐付け'
            WHEN 'participants' THEN '参加者'
            WHEN 'flows' THEN 'フロー'
            WHEN 'flow_steps' THEN 'フローステップ'
            WHEN 'step_candidates' THEN 'ステップ候補'
            WHEN 'flow_templates' THEN 'フローテンプレート'
            WHEN 'flow_template_steps' THEN 'フローテンプレートステップ'
            ELSE CONCAT('論理名未設定: ', v_table_name)
        END;

        SET @sql = CONCAT(
            'ALTER TABLE `', @schema_name, '`.`', v_table_name,
            '` COMMENT = ', QUOTE(v_comment)
        );
        PREPARE s FROM @sql;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END LOOP;
    CLOSE c_tables;

    -- column comments
    SET done = 0;
    OPEN c_columns;
    loop_columns: LOOP
        FETCH c_columns INTO
            v_table_name, v_column_name, v_column_type, v_is_nullable,
            v_column_default, v_extra, v_character_set_name, v_collation_name;

        IF done = 1 THEN
            LEAVE loop_columns;
        END IF;

        SET v_comment = CASE
            WHEN v_table_name = 'users' AND v_column_name = 'id' THEN 'ユーザーID'
            WHEN v_table_name = 'users' AND v_column_name = 'username' THEN 'ユーザー名'
            WHEN v_table_name = 'users' AND v_column_name = 'password' THEN 'パスワード'
            WHEN v_table_name = 'users' AND v_column_name = 'enabled' THEN '有効フラグ'
            WHEN v_table_name = 'users' AND v_column_name = 'created_at' THEN '作成日時'
            WHEN v_table_name = 'users' AND v_column_name = 'updated_at' THEN '更新日時'
            WHEN v_table_name = 'users' AND v_column_name = 'deleted_at' THEN '削除日時'

            WHEN v_table_name = 'roles' AND v_column_name = 'id' THEN 'ロールID'
            WHEN v_table_name = 'roles' AND v_column_name = 'name' THEN 'ロール名'
            WHEN v_table_name = 'roles' AND v_column_name = 'created_at' THEN '作成日時'
            WHEN v_table_name = 'roles' AND v_column_name = 'updated_at' THEN '更新日時'

            WHEN v_table_name = 'user_roles' AND v_column_name = 'id' THEN 'ユーザーロールID'
            WHEN v_table_name = 'user_roles' AND v_column_name = 'user_id' THEN 'ユーザーID'
            WHEN v_table_name = 'user_roles' AND v_column_name = 'role_id' THEN 'ロールID'
            WHEN v_table_name = 'user_roles' AND v_column_name = 'created_at' THEN '作成日時'

            WHEN v_table_name = 'participants' AND v_column_name = 'id' THEN '参加者ID'
            WHEN v_table_name = 'participants' AND v_column_name = 'participant_type' THEN '参加者種別'
            WHEN v_table_name = 'participants' AND v_column_name = 'user_id' THEN 'ユーザーID'
            WHEN v_table_name = 'participants' AND v_column_name = 'display_name' THEN '表示名'
            WHEN v_table_name = 'participants' AND v_column_name = 'email' THEN 'メールアドレス'
            WHEN v_table_name = 'participants' AND v_column_name = 'timezone' THEN 'タイムゾーン'
            WHEN v_table_name = 'participants' AND v_column_name = 'created_at' THEN '作成日時'
            WHEN v_table_name = 'participants' AND v_column_name = 'updated_at' THEN '更新日時'

            WHEN v_table_name = 'flows' AND v_column_name = 'id' THEN 'フローID'
            WHEN v_table_name = 'flows' AND v_column_name = 'title' THEN 'タイトル'
            WHEN v_table_name = 'flows' AND v_column_name = 'duration_minutes' THEN '所要時間(分)'
            WHEN v_table_name = 'flows' AND v_column_name = 'status' THEN 'ステータス'
            WHEN v_table_name = 'flows' AND v_column_name = 'current_step_order' THEN '現在ステップ順'
            WHEN v_table_name = 'flows' AND v_column_name = 'start_from' THEN '開始予定日時'
            WHEN v_table_name = 'flows' AND v_column_name = 'created_by_user_id' THEN '作成者ユーザーID'
            WHEN v_table_name = 'flows' AND v_column_name = 'source_template_id' THEN '元テンプレートID'
            WHEN v_table_name = 'flows' AND v_column_name = 'source_template_name_snapshot' THEN '元テンプレート名スナップショット'
            WHEN v_table_name = 'flows' AND v_column_name = 'step_cycle_size' THEN 'ステップ巡回人数'
            WHEN v_table_name = 'flows' AND v_column_name = 'created_at' THEN '作成日時'
            WHEN v_table_name = 'flows' AND v_column_name = 'updated_at' THEN '更新日時'
            WHEN v_table_name = 'flows' AND v_column_name = 'deleted_at' THEN '削除日時'
            WHEN v_table_name = 'flows' AND v_column_name = 'version' THEN 'バージョン'

            WHEN v_table_name = 'flow_steps' AND v_column_name = 'id' THEN 'フローステップID'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'flow_id' THEN 'フローID'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'step_order' THEN 'ステップ順'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'participant_id' THEN '参加者ID'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'participant_name' THEN '参加者名'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'status' THEN 'ステータス'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'confirmed_start_at' THEN '確定開始日時'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'confirmed_end_at' THEN '確定終了日時'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'created_at' THEN '作成日時'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'updated_at' THEN '更新日時'
            WHEN v_table_name = 'flow_steps' AND v_column_name = 'version' THEN 'バージョン'

            WHEN v_table_name = 'step_candidates' AND v_column_name = 'id' THEN 'ステップ候補ID'
            WHEN v_table_name = 'step_candidates' AND v_column_name = 'flow_step_id' THEN 'フローステップID'
            WHEN v_table_name = 'step_candidates' AND v_column_name = 'start_at' THEN '開始候補日時'
            WHEN v_table_name = 'step_candidates' AND v_column_name = 'end_at' THEN '終了候補日時'
            WHEN v_table_name = 'step_candidates' AND v_column_name = 'status' THEN 'ステータス'
            WHEN v_table_name = 'step_candidates' AND v_column_name = 'created_at' THEN '作成日時'
            WHEN v_table_name = 'step_candidates' AND v_column_name = 'updated_at' THEN '更新日時'
            WHEN v_table_name = 'step_candidates' AND v_column_name = 'selected_at' THEN '選択日時'
            WHEN v_table_name = 'step_candidates' AND v_column_name = 'version' THEN 'バージョン'

            WHEN v_table_name = 'flow_templates' AND v_column_name = 'id' THEN 'フローテンプレートID'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'name' THEN 'テンプレート名'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'description' THEN '説明'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'duration_minutes' THEN '所要時間(分)'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'created_by_user_id' THEN '作成者ユーザーID'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'visibility' THEN '公開範囲'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'is_active' THEN '有効フラグ'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'last_used_at' THEN '最終使用日時'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'created_at' THEN '作成日時'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'updated_at' THEN '更新日時'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'deleted_at' THEN '削除日時'
            WHEN v_table_name = 'flow_templates' AND v_column_name = 'version' THEN 'バージョン'

            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'id' THEN 'テンプレートステップID'
            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'template_id' THEN 'フローテンプレートID'
            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'step_order' THEN 'ステップ順'
            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'participant_id' THEN '参加者ID'
            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'participant_type_snapshot' THEN '参加者種別スナップショット'
            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'participant_name_snapshot' THEN '参加者名スナップショット'
            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'created_at' THEN '作成日時'
            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'updated_at' THEN '更新日時'
            WHEN v_table_name = 'flow_template_steps' AND v_column_name = 'version' THEN 'バージョン'

            ELSE CONCAT('論理名未設定: ', v_column_name)
        END;

        SET v_definition = v_column_type;
        IF v_character_set_name IS NOT NULL THEN
            SET v_definition = CONCAT(v_definition, ' CHARACTER SET ', v_character_set_name);
        END IF;
        IF v_collation_name IS NOT NULL THEN
            SET v_definition = CONCAT(v_definition, ' COLLATE ', v_collation_name);
        END IF;
        IF v_is_nullable = 'NO' THEN
            SET v_definition = CONCAT(v_definition, ' NOT NULL');
        ELSE
            SET v_definition = CONCAT(v_definition, ' NULL');
        END IF;
        IF v_column_default IS NOT NULL THEN
            IF UPPER(v_column_default) REGEXP '^CURRENT_TIMESTAMP(\\([0-9]+\\))?$' THEN
                SET v_definition = CONCAT(v_definition, ' DEFAULT ', v_column_default);
            ELSE
                SET v_definition = CONCAT(v_definition, ' DEFAULT ', QUOTE(v_column_default));
            END IF;
        ELSEIF v_is_nullable = 'YES' THEN
            SET v_definition = CONCAT(v_definition, ' DEFAULT NULL');
        END IF;
        IF LOCATE('auto_increment', LOWER(IFNULL(v_extra, ''))) > 0 THEN
            SET v_definition = CONCAT(v_definition, ' AUTO_INCREMENT');
        END IF;
        IF LOCATE('on update', LOWER(IFNULL(v_extra, ''))) > 0 THEN
            SET v_definition = CONCAT(v_definition, ' ', SUBSTRING(v_extra, LOCATE('on update', LOWER(v_extra))));
        END IF;

        SET @sql = CONCAT(
            'ALTER TABLE `', @schema_name, '`.`', v_table_name,
            '` MODIFY COLUMN `', v_column_name, '` ',
            v_definition,
            ' COMMENT ', QUOTE(v_comment)
        );
        PREPARE s FROM @sql;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END LOOP;
    CLOSE c_columns;
END $$

CALL sp_fill_logical_comments(DATABASE(), FALSE) $$
DROP PROCEDURE sp_fill_logical_comments $$

DELIMITER ;
