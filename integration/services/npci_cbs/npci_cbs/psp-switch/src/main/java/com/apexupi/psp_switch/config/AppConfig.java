package com.apexupi.psp_switch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    // Single shared RestTemplate bean — used by CBSAdapter and NPCIAdapter
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Thread pool for @Async orchestrator callbacks
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("psp-async-");
        executor.initialize();
        return executor;
    }
}