package com.hpe.upi.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.upi.webhook.model.WebhookRegistration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebhookDeliveryService — delivers signed webhook payloads to registered URLs.
 *
 * Features:
 *  - HMAC-SHA256 signature on every payload (X-UPI-Signature header)
 *  - 3 delivery attempts with exponential backoff (1s, 2s, 4s)
 *  - Delivery log per registration (last 50 attempts)
 *  - In-memory registration store (replace with DB for production)
 *  - Async delivery — Kafka consumer returns immediately, delivery happens in background
 *
 * Terminal events that trigger webhooks: SUCCESS, REVERSED, DEBIT_FAILED, VALIDATION_FAILED
 */
@Service
public class WebhookDeliveryService {

    private static final int MAX_RETRIES = 3;
    private static final Set<String> TERMINAL_STATUSES = Set.of(
        "SUCCESS", "REVERSED", "DEBIT_FAILED", "VALIDATION_FAILED", "REVERSAL_FAILED"
    );

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    // In-memory store: registrationId → registration
    private final Map<String, WebhookRegistration> registrations = new ConcurrentHashMap<>();

    // Delivery log: registrationId → list of recent delivery attempts
    private final Map<String, List<Map<String, Object>>> deliveryLogs = new ConcurrentHashMap<>();

    /**
     * Called by the Kafka consumer. Checks if the event is terminal,
     * finds interested registrations, delivers asynchronously.
     */
    public void handleTransactionEvent(Map<String, Object> event) {
        String status = (String) event.get("status");
        String txnId  = (String) event.get("txnId");

        if (status == null || !TERMINAL_STATUSES.contains(status)) {
            return; // only deliver on terminal events
        }

        System.out.println("[WEBHOOK] Terminal event: " + txnId + " status=" + status);

        registrations.values().stream()
            .filter(WebhookRegistration::isActive)
            .filter(reg -> reg.isInterestedIn(status))
            .forEach(reg -> deliverAsync(reg, event));
    }

    /**
     * Async delivery with exponential backoff retry.
     * Fires and forgets from the Kafka consumer's perspective.
     */
    @Async
    public void deliverAsync(WebhookRegistration registration, Map<String, Object> event) {
        String txnId = (String) event.get("txnId");
        String payload;
        try {
            payload = mapper.writeValueAsString(buildPayload(event));
        } catch (Exception e) {
            System.err.println("[WEBHOOK] Failed to serialize payload: " + e.getMessage());
            return;
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 1) {
                    long backoffMs = (long) Math.pow(2, attempt - 1) * 1000L;
                    System.out.println("[WEBHOOK] Retry " + attempt + " for " + txnId
                        + " — waiting " + backoffMs + "ms");
                    Thread.sleep(backoffMs);
                }

                String signature = sign(payload, registration.getSecret());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-UPI-Signature", "sha256=" + signature);
                headers.set("X-UPI-TxnId", txnId);
                headers.set("X-UPI-Attempt", String.valueOf(attempt));
                headers.set("X-UPI-Timestamp", Instant.now().toString());

                HttpEntity<String> request = new HttpEntity<>(payload, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(
                    registration.getTargetUrl(), request, String.class);

                boolean success = response.getStatusCode().is2xxSuccessful();
                logDelivery(registration.getId(), txnId, attempt, success,
                    response.getStatusCode().value(), null);

                if (success) {
                    System.out.println("[WEBHOOK] Delivered to " + registration.getOwner()
                        + " | txnId=" + txnId + " | attempt=" + attempt
                        + " | status=" + response.getStatusCode());
                    return;
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                System.err.println("[WEBHOOK] Delivery failed (attempt " + attempt + ") for "
                    + txnId + " to " + registration.getTargetUrl() + ": " + e.getMessage());
                logDelivery(registration.getId(), txnId, attempt, false, 0, e.getMessage());
            }
        }

        System.err.println("[WEBHOOK] All " + MAX_RETRIES + " attempts exhausted for " + txnId
            + " to " + registration.getOwner());
    }

    /**
     * Build the webhook payload — wraps the event in a standard envelope.
     */
    private Map<String, Object> buildPayload(Map<String, Object> event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event",     "UPI_TRANSACTION_UPDATE");
        payload.put("timestamp", Instant.now().toString());
        payload.put("data",      event);
        return payload;
    }

    /**
     * HMAC-SHA256 signature — recipient can verify using their registered secret.
     * Header format: X-UPI-Signature: sha256=<hex>
     */
    private String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private void logDelivery(String regId, String txnId, int attempt,
                              boolean success, int httpStatus, String error) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("txnId",      txnId);
        log.put("attempt",    attempt);
        log.put("success",    success);
        log.put("httpStatus", httpStatus);
        log.put("error",      error);
        log.put("at",         Instant.now().toString());

        deliveryLogs.computeIfAbsent(regId, k -> new CopyOnWriteArrayList<>()).add(0, log);
        List<Map<String, Object>> logs = deliveryLogs.get(regId);
        if (logs.size() > 50) logs.remove(logs.size() - 1);
    }

    // ── Registration management ──────────────────────────────────────────────

    public WebhookRegistration register(WebhookRegistration reg) {
        registrations.put(reg.getId(), reg);
        System.out.println("[WEBHOOK] Registered: " + reg.getOwner() + " → " + reg.getTargetUrl());
        return reg;
    }

    public boolean deactivate(String id) {
        WebhookRegistration reg = registrations.get(id);
        if (reg == null) return false;
        reg.setActive(false);
        return true;
    }

    public List<WebhookRegistration> getAllRegistrations() {
        return new ArrayList<>(registrations.values());
    }

    public List<Map<String, Object>> getDeliveryLogs(String regId) {
        return deliveryLogs.getOrDefault(regId, Collections.emptyList());
    }
}
