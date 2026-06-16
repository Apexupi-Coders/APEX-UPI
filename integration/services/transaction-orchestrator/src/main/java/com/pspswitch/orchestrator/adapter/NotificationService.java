package com.pspswitch.orchestrator.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
<<<<<<< HEAD
=======
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
>>>>>>> c24d976 (Initial commit)
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

<<<<<<< HEAD
    @Value("${webhook.tpap.url:http://localhost:8080/api/v1/tpap/callback}")
=======
    @Value("${webhook.tpap.url:http://localhost:8080/tpap/api/v1/tpap/callback}")
>>>>>>> c24d976 (Initial commit)
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
<<<<<<< HEAD
            restTemplate.postForEntity(tpapWebhookUrl, payload, String.class);
            log.info("[WEBHOOK_OUT] Successfully hit TPAP Webhook: {} for tid={}", tpapWebhookUrl, tid);
=======
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // TPAP ingress validates registry key against X-TPAP-ID (see TpapAuthService).
            headers.add("X-TPAP-ID", "phonepe");

            HttpEntity<NotificationPayload> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(tpapWebhookUrl, request, String.class);
            log.info("[WEBHOOK_OUT] Successfully hit TPAP Webhook: {} for tid={} with X-TPAP-ID=phonepe", tpapWebhookUrl, tid);
>>>>>>> c24d976 (Initial commit)
        } catch (Exception e) {
            log.warn("[WEBHOOK_OUT] Unable to reach TPAP at {}: {}", tpapWebhookUrl, e.getMessage());
        }
    }

    // Java 17 Record for clean JSON payload generation
    public record NotificationPayload(String tid, String pa, String state, BigDecimal amount, String reason) {
    }
}
