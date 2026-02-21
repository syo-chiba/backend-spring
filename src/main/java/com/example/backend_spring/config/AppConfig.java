package com.example.backend_spring.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    Clock appClock() {
        return Clock.systemDefaultZone();
    }
}
