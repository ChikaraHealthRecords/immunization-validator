package com.immunization.validator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Immunization Validator Service.
 * 
 * This service validates immunization records against configurable state requirements
 * without requiring authentication or storing personally identifiable information.
 */
@SpringBootApplication
public class ImmunizationValidatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImmunizationValidatorApplication.class, args);
    }
}

