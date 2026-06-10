package com.hpe.upi.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Webhook Service — independent microservice.
 *
 * Listens to Kafka for terminal transaction events (SUCCESS, REVERSED, FAILED)
 * and POSTs signed payloads to registered external URLs (PSP apps, merchants).
 *
 * Port: 8085
 */
@SpringBootApplication
@EnableAsync
public class WebhookApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebhookApplication.class, args);
    }
}
