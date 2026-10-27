package com.immunization.validator.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Switches on the JPA repositories only in database mode.
 * Moved here from the application class so the app can start with no database at all.
 */
@Configuration
@ConditionalOnProperty(name = "immunization.vaccines.source", havingValue = "database")
@EnableJpaRepositories("com.immunization.validator.repository")
public class VaccineDatabaseConfig {
}
