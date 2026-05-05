package com.apexupi.psp_switch.cbs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Async webhook dispatcher for CBS Engine.
 *
 * After CBS executes an operation, it fires this dispatcher in a
 * background thread to simulate the real-world async CBS response.
 *
 * Webhook target: POST /webhook/cbs/{txnId}
 * Payload: { "status": "DEBIT_SUCCESS" | "DEBIT_FAILED" | "CREDIT_SUCCESS" |
 *             "CREDIT_FAILED" | "REVERSAL_SUCCESS" | "REVERSAL_FAILED" }
 *
 * This matches exactly what WebhookController.cbsCallback() expects.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CbsCallbackDispatcher {

    private final RestTemplate restTemplate;

    // Simulated CBS processing delay range (ms)
    private static final long MIN_DELAY_MS = 800;
    private static final long MAX_DELAY_MS = 2000;

    /**
     * Fire-and-forget: execute the result callback asynchronously after delay.
     * Called by CbsEngine after operation execution.
     */
    public void dispatchAsync(CbsOperationResult result) {
        Thread thread = new Thread(() -> {
            try {
                // Simulate CBS internal processing latency
                long delay = MIN_DELAY_MS + (long) (Math.random() * (MAX_DELAY_MS - MIN_DELAY_MS));
                log.info("[CBS][DISPATCHER] Scheduling callback txnId={} op={} delay={}ms",
                        result.getTxnId(), result.getOperation(), delay);
                Thread.sleep(delay);

                deliverWebhook(result);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[CBS][DISPATCHER] Interrupted for txnId={}", result.getTxnId());
            }
        }, "cbs-callback-" + result.getTxnId().substring(0, 8));

        thread.setDaemon(true);
        thread.start();
    }

    private void deliverWebhook(CbsOperationResult result) {
        String url = "http://localhost:8080/webhook/cbs/" + result.getTxnId();
        String status = result.toWebhookStatus();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Include reason in payload so WebhookController can log it
        Map<String, String> body = result.getReason() != null
                ? Map.of("status", status, "reason", result.getReason())
                : Map.of("status", status);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("[CBS][DISPATCHER] Webhook delivered txnId={} status={} http={}",
                    result.getTxnId(), status, response.getStatusCode());
        } catch (Exception e) {
            log.error("[CBS][DISPATCHER] Webhook delivery FAILED txnId={} status={}",
                    result.getTxnId(), status, e);
        }
    }
}