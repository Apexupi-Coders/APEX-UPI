package com.pspswitch.orchestrator.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Notification Service — Step 10 of the orchestration saga.
 * 
 * Sends a real HTTP POST webhook to the TPAP (API Gateway / Ingress Service)
 * to notify about the transaction state.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${webhook.tpap.url:http://localhost:8080/api/v1/tpap/callback}")
    private String tpapWebhookUrl;

    /**
     * Sends a notification about a transaction state change.
     */
    public void notify(String tid, String pa, BigDecimal am, String state) {
        log.info("[WEBHOOK_OUT] tid={} | state={} | payee={} | amount={}", tid, state, pa, am);
        sendWebhook(tid, pa, state, am, null);
    }

    /**
     * Sends a failure notification.
     */
    public void notifyFailure(String tid, String pa, String reason) {
        log.info("[WEBHOOK_OUT] tid={} | state=FAILED | payee={} | reason={}", tid, pa, reason);
        sendWebhook(tid, pa, "FAILED", null, reason);
    }

    /**
     * Sends a compensation notification.
     */
    public void notifyCompensation(String tid, String pa, BigDecimal am) {
        log.info("[WEBHOOK_OUT] tid={} | state=COMPENSATED | payee={} | amount={} | Reversal initiated", tid, pa, am);
        sendWebhook(tid, pa, "COMPENSATED", am, "Saga reverted");
    }

    private void sendWebhook(String tid, String pa, String state, BigDecimal am, String reason) {
        try {
            NotificationPayload payload = new NotificationPayload(tid, pa, state, am, reason);
            restTemplate.postForEntity(tpapWebhookUrl, payload, String.class);
            log.info("[WEBHOOK_OUT] Successfully hit TPAP Webhook: {} for tid={}", tpapWebhookUrl, tid);
        } catch (Exception e) {
            log.warn("[WEBHOOK_OUT] Unable to reach TPAP at {}: {}", tpapWebhookUrl, e.getMessage());
        }
    }

    // Java 17 Record for clean JSON payload generation
    public record NotificationPayload(String tid, String pa, String state, BigDecimal amount, String reason) {
    }
}
