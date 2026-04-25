package com.example.docextract;

import com.example.docextract.config.ApplicationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ApplicationProperties.class)
public class DocumentExtractionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentExtractionApplication.class, args);
    }
}
