package com.aditya.dataautomation.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev/Local only: automatically repairs mismatched migration checksums caused by
 * local edits or CRLF/LF line ending changes before migrating.
 * In production (SPRING_PROFILES_ACTIVE=prod), this bean is bypassed and strict Flyway validation is enforced.
 */
@Configuration
@Profile("!prod")
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
