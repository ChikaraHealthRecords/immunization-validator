package com.immunization.validator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded async executor for document extraction polling/processing, so it
 * never competes unbounded with request-handling threads. Also enables the
 * @Scheduled sweep that catches non-terminal jobs (e.g. after an app restart).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "extractionTaskExecutor")
    public ThreadPoolTaskExecutor extractionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("extraction-");
        executor.initialize();
        return executor;
    }
}
