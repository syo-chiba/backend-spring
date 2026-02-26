package com.example.backend_spring.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DbMaintenanceRunner {

    private static final Logger log = LoggerFactory.getLogger(DbMaintenanceRunner.class);

    @Bean
    CommandLineRunner ensureDatabaseIndexesAndFks(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return args -> {
            try (Connection con = dataSource.getConnection()) {
                DatabaseMetaData meta = con.getMetaData();
                String product = meta.getDatabaseProductName();
                if (product == null || !product.toLowerCase().contains("mysql")) {
                    log.info("Skip DB maintenance: non-MySQL database detected [{}]", product);
                    return;
                }
            }

            ensureIndex(jdbcTemplate,
                    "step_candidates",
                    "idx_step_candidates_flow_step_id_start_at",
                    "CREATE INDEX idx_step_candidates_flow_step_id_start_at ON step_candidates (flow_step_id, start_at)");

            ensureIndex(jdbcTemplate,
                    "step_candidates",
                    "idx_step_candidates_status_start_end",
                    "CREATE INDEX idx_step_candidates_status_start_end ON step_candidates (status, start_at, end_at)");

            ensureIndex(jdbcTemplate,
                    "flow_steps",
                    "idx_flow_steps_flow_id_step_order",
                    "CREATE INDEX idx_flow_steps_flow_id_step_order ON flow_steps (flow_id, step_order)");

            ensureIndex(jdbcTemplate,
                    "flows",
                    "idx_flows_created_by_user_id",
                    "CREATE INDEX idx_flows_created_by_user_id ON flows (created_by_user_id)");

            ensureForeignKey(jdbcTemplate,
                    "flow_steps",
                    "fk_flow_steps_flow",
                    "ALTER TABLE flow_steps ADD CONSTRAINT fk_flow_steps_flow FOREIGN KEY (flow_id) REFERENCES flows(id) ON DELETE CASCADE");

            ensureForeignKey(jdbcTemplate,
                    "step_candidates",
                    "fk_step_candidates_flow_step",
                    "ALTER TABLE step_candidates ADD CONSTRAINT fk_step_candidates_flow_step FOREIGN KEY (flow_step_id) REFERENCES flow_steps(id) ON DELETE CASCADE");
        };
    }

    private void ensureIndex(JdbcTemplate jdbcTemplate, String table, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                table,
                indexName);
        if (count != null && count == 0) {
            jdbcTemplate.execute(ddl);
            log.info("Created index [{}] on table [{}]", indexName, table);
        }
    }

    private void ensureForeignKey(JdbcTemplate jdbcTemplate, String table, String fkName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = ? AND constraint_name = ? AND constraint_type = 'FOREIGN KEY'",
                Integer.class,
                table,
                fkName);
        if (count != null && count == 0) {
            jdbcTemplate.execute(ddl);
            log.info("Created foreign key [{}] on table [{}]", fkName, table);
        }
    }
}
