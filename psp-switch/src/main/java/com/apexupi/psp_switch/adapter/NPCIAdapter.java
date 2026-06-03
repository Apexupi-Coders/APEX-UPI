package com.apexupi.psp_switch.adapter;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NPCIAdapter {

    private final JpaTransactionRepository transactionRepo;
    private final RestTemplate restTemplate;  // ← injected Spring bean, not new RestTemplate()

    public void submitTransaction(String txnId) {
        log.info("[NPCI_ADAPTER] action=submit txnId={}", txnId);

        TransactionEntity txn = transactionRepo.findById(txnId).orElseThrow();
        txn.setState(TransactionState.NPCI_CALLED);
        txn.setUpdatedAt(java.time.LocalDateTime.now());
        transactionRepo.save(txn);

        // Simulate async NPCI processing — fires callback after delay
        new Thread(() -> simulateNpciCallback(txnId)).start();
    }

    private void simulateNpciCallback(String txnId) {
        try {
            // Simulate NPCI network latency: 2–5 seconds
            Thread.sleep(2000 + (long) (Math.random() * 3000));

            // 95% success rate
            String status = (Math.random() > 0.05) ? "SUCCESS" : "FAILED";

            log.info("[NPCI_ADAPTER] callback firing txnId={} status={}", txnId, status);

            // POST to webhook — this triggers the orchestrator via WebhookController
            callWebhook(txnId, status);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[NPCI_ADAPTER] thread interrupted txnId={}", txnId);
        } catch (Exception e) {
            log.error("[NPCI_ADAPTER] simulation error txnId={}", txnId, e);
        }
    }

    private void callWebhook(String txnId, String status) {
        // Must match WebhookController: POST /webhook/npci/{txnId}
        String url = "http://localhost:8080/webhook/npci/" + txnId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("status", status);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("[NPCI_ADAPTER] webhook delivered txnId={} status={} http={}",
                    txnId, status, response.getStatusCode());
        } catch (Exception e) {
            log.error("[NPCI_ADAPTER] webhook delivery FAILED txnId={} url={}", txnId, url, e);
        }
    }

    public boolean validate(String payer, String payee) {
        log.info("[NPCI_ADAPTER] validate payer={} payee={}", payer, payee);
        if (payer == null || payee == null || payer.isBlank() || payee.isBlank()) {
            return false;
        }
        //return Math.random() > 0.05; // 95% success
         return Math.random() > 0.95;


    }

    public String resolveBank(String upiId) {
        log.debug("[NPCI_ADAPTER] resolveBank upiId={}", upiId);
        return upiId.contains("hdfc") ? "HDFC" : upiId.contains("sbi") ? "SBI" : "UNKNOWN";
    }
}