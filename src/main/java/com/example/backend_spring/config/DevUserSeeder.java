package com.example.backend_spring.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.backend_spring.domain.UserAccount;
import com.example.backend_spring.repository.UserAccountRepository;

@Configuration
public class DevUserSeeder {

    @Bean
    CommandLineRunner seed(UserAccountRepository repo, PasswordEncoder encoder, JdbcTemplate jdbcTemplate, DataSource dataSource) {
        return args -> {
            UserAccount admin = repo.findByUsername("admin")
                    .orElseGet(() -> repo.save(new UserAccount(
                            "admin",
                            encoder.encode("admin123"),
                            true)));

            try (Connection con = dataSource.getConnection()) {
                DatabaseMetaData meta = con.getMetaData();
                String product = meta.getDatabaseProductName();
                if (product == null || !product.toLowerCase().contains("mysql")) {
                    return;
                }
            }

            jdbcTemplate.update("""
                    INSERT INTO roles(name)
                    SELECT 'ROLE_ADMIN'
                    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN')
                    """);

            jdbcTemplate.update("""
                    INSERT INTO user_roles(user_id, role_id, created_at)
                    SELECT ?, r.id, CURRENT_TIMESTAMP(6)
                    FROM roles r
                    WHERE r.name = 'ROLE_ADMIN'
                      AND NOT EXISTS (
                          SELECT 1 FROM user_roles ur
                          WHERE ur.user_id = ? AND ur.role_id = r.id
                      )
                    """, admin.getId(), admin.getId());
        };
    }
}
