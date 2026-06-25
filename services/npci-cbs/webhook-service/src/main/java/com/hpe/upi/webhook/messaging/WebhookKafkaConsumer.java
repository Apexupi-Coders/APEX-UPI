package com.hpe.upi.webhook.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.upi.webhook.service.WebhookDeliveryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WebhookKafkaConsumer — listens to transaction status events.
 *
 * Subscribes to upi.transactions.status (published by NPCI on SUCCESS/REVERSED)
 * and upi.dashboard.events (published by all services — catches DEBIT_FAILED etc.)
 *
 * Passes every event to WebhookDeliveryService which decides if it is terminal
 * and which registrations are interested.
 */
@Component
public class WebhookKafkaConsumer {

    private final WebhookDeliveryService deliveryService;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public WebhookKafkaConsumer(WebhookDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = "upi.transactions.status", groupId = "webhook-service")
    public void onTransactionStatus(String message) {
        try {
            Map<String, Object> event = mapper.readValue(message, new TypeReference<>() {});
            System.out.println("[WEBHOOK-CONSUMER] Transaction status event: "
                + event.get("txnId") + " | " + event.get("status"));
            deliveryService.handleTransactionEvent(event);
        } catch (Exception e) {
            System.err.println("[WEBHOOK-CONSUMER] Error processing status event: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "upi.dashboard.events", groupId = "webhook-service-dashboard")
    public void onDashboardEvent(String message) {
        try {
            Map<String, Object> event = mapper.readValue(message, new TypeReference<>() {});
            // dashboard events include all statuses — delivery service filters terminal ones
            deliveryService.handleTransactionEvent(event);
        } catch (Exception e) {
            System.err.println("[WEBHOOK-CONSUMER] Error processing dashboard event: " + e.getMessage());
        }
    }
}
