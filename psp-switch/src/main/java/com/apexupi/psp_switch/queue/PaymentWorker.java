package com.apexupi.psp_switch.queue;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.apexupi.psp_switch.model.DlqEntry;
import com.apexupi.psp_switch.repository.TransactionStore;
import com.apexupi.psp_switch.service.DlqService;
import com.apexupi.psp_switch.service.TransactionOrchestratorService;
import com.apexupi.psp_switch.service.MonitoringService;
import com.apexupi.psp_switch.service.WebhookService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PaymentWorker {

    private final PaymentQueue queue;
    private final TransactionStore store;
    private final DlqService dlqService;
    private final WebhookService webhookService;
    private final TransactionOrchestratorService orchestrator;
    private final MonitoringService monitoringService;
    
    @Value("${app.dlq.backoff-initial-ms:2000}")
    private long initialBackoffMs;
    
    @Value("${app.dlq.backoff-max-ms:32000}")
    private long maxBackoffMs;
    
    public PaymentWorker(PaymentQueue queue, TransactionStore store, DlqService dlqService, WebhookService webhookService, TransactionOrchestratorService orchestrator, MonitoringService monitoringService) {
        this.queue = queue;
        this.store = store;
        this.dlqService = dlqService;
        this.webhookService = webhookService;
        this.orchestrator = orchestrator;
        this.monitoringService = monitoringService;
    }


    @PostConstruct
    public void startWorker() {
        new Thread(() -> {
            while (true) {
                try {
                    String txnId = queue.consume();

                    log.info("Processing txn status=PENDING: {}", txnId);
                    store.updateStatus(txnId, "PENDING");

                    var request = store.getTransactionDetails(txnId);

                    if (request == null) {
                        log.error("Invalid txn, no details found: {}", txnId);
                        store.updateStatus(txnId, "FAILED");
                        continue;
                    }

                    // Add observability event
                    monitoringService.addEvent(txnId, "RETRYING");

                    // Use Saga Orchestrator instead of synchronous transfer
                    orchestrator.orchestrate(txnId);


                    // Check Saga result status
                    String status = store.getStatus(txnId);
                    String failureReason = store.getFailureReason(txnId);

                    if ("SUCCESS".equals(status)) {
                        webhookService.notifyClient(txnId, "SUCCESS");
                        log.info("Saga SUCCESS txn: {}", txnId);
                    } else if ("COMPENSATED".equals(status)) {
                        webhookService.notifyClient(txnId, "COMPENSATED");
                        log.warn("Saga COMPENSATED txn: {}", txnId);
                    } else {
                        int retries = store.getRetryCount(txnId);
                        store.updateStatus(txnId, "RETRYING");

                        log.warn("Saga failed, retries={}, reason={}, status={}: {}", retries, failureReason, status, txnId);

                        if (retries < store.getMaxRetries()) {
                            store.incrementRetry(txnId);
                            long backoffMs = Math.min(initialBackoffMs * (1L << retries), maxBackoffMs);
                            log.info("Saga retry backoff {}ms attempt {}/{}: {}", backoffMs, retries + 1, store.getMaxRetries(), txnId);
                            Thread.sleep(backoffMs);
                            queue.publish(txnId);
                        } else {
                            store.updateStatus(txnId, "FAILED");
                            DlqEntry dlqEntry = DlqEntry.builder()
                                    .txnId(txnId)
                                    .payer(request.getPayer())
                                    .payee(request.getPayee())
                                    .amount(request.getAmount())
                                    .failureReason(failureReason)
                                    .retryCount(retries)
                                    .timestamp(LocalDateTime.now())
                                    .status("FAILED")
                                    .build();
                            monitoringService.addEvent(txnId, "DLQ");
                            dlqService.sendToDLQ(dlqEntry);
                            webhookService.notifyClient(txnId, "FAILED");
                            log.error("Saga moved to DLQ after {} retries: {}", retries, txnId);

                        }
                    }

                } catch (Exception e) {
                    log.error("Worker error: ", e);
                }
            }
        }).start();
    }
}
