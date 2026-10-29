package com.immunization.validator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Which websites may call the API from a browser.
 *
 * Set immunization.cors.allowed-origins (or the IMMUNIZATION_CORS_ALLOWED_ORIGINS
 * environment variable) to a comma-separated list, e.g.
 * https://validator.chikarahealthrecords.info. The default allows only local
 * development servers.
 *
 * The API needs no cookies or credentials, so they're not allowed cross-origin.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    static final String DEFAULT_ALLOWED_ORIGINS =
            "http://localhost:5173,http://localhost:4173,http://localhost:3000,http://localhost:5174";

    @Value("${immunization.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
