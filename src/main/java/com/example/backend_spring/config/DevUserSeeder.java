package com.example.backend_spring.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.backend_spring.domain.UserAccount;
import com.example.backend_spring.repository.UserAccountRepository;

@Configuration
public class DevUserSeeder {

    @Bean
    CommandLineRunner seed(UserAccountRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.findByUsername("admin").isEmpty()) {
                repo.save(new UserAccount(
                        "admin",
                        encoder.encode("admin123"),
                        true,
                        "ROLE_ADMIN"
                ));
            }
        };
    }
}
