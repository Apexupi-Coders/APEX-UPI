package com.apexupi.psp_switch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.apexupi.psp_switch.model.PaymentRequest;
import com.apexupi.psp_switch.model.TransactionContext;
import com.apexupi.psp_switch.model.TransactionResult;
import com.apexupi.psp_switch.repository.TransactionStore;
import com.apexupi.psp_switch.service.MonitoringService;
import com.apexupi.psp_switch.adapter.NPCIAdapter;
import com.apexupi.psp_switch.service.PSPProcessor;

@Service
@Slf4j
public class TransactionOrchestratorService {

    private final TransactionStore store;
    private final PSPProcessor pspProcessor;
    private final MonitoringService monitoringService;
    private final NPCIAdapter npciAdapter;

    public TransactionOrchestratorService(TransactionStore store, PSPProcessor pspProcessor, MonitoringService monitoringService, NPCIAdapter npciAdapter) {
        this.store = store;
        this.pspProcessor = pspProcessor;
        this.monitoringService = monitoringService;
        this.npciAdapter = npciAdapter;
    }

    /**
     * Production-grade Saga Orchestrator using Compensating Transactions pattern.
     * 
     * Saga Flow:
     * PENDING → PROCESSING
     *   ↓ debit() - Payer PSP debit (NPCI debit API)
     * DEBIT_SUCCESS
     *   ↓ credit() - Payee PSP credit (NPCI credit API)
     *   → SUCCESS
     *   → Credit fails: rollbackDebit() → COMPENSATED 
     * 
     * Key Properties:
     * - Eventual consistency (no global lock)
     * - Idempotent compensating actions
     * - Step-specific failure isolation
     * - Integrates with existing retry/DLQ in PaymentWorker
     * 
     * @param txnId Transaction ID from PaymentRequest
     */
    public void orchestrate(String txnId) {
        monitoringService.addEvent(txnId, "SUBMITTED");
        log.info("🧿 Saga orchestration STARTED for txnId={}", txnId);
        
        PaymentRequest req = store.getTransactionDetails(txnId);
        if (req == null) {
            store.updateStatus(txnId, "FAILED");
            store.setFailureReason(txnId, "NO_TRANSACTION_DETAILS");
            monitoringService.addEvent(txnId, "FAILED");
            log.error("Saga failed: no details for txnId={}", txnId);
            return;
        }
        
        String payer = req.getPayer();
        String payee = req.getPayee();
        double amount = req.getAmount();
        
        monitoringService.addEvent(txnId, "PROCESSING");
        
        // NPCI validation
        if (!npciAdapter.validate(payer, payee)) {
            store.updateStatus(txnId, "FAILED");
            store.setFailureReason(txnId, "NPCI_FAILED");
            monitoringService.addEvent(txnId, "NPCI_FAILED");
            log.warn("NPCI validation failed: txnId={}", txnId);
            return;
        }
        monitoringService.addEvent(txnId, "NPCI_SUCCESS");
        
        // Random UNKNOWN simulation (timeout)
        if (Math.random() < 0.05) {
            monitoringService.addEvent(txnId, "UNKNOWN");
            store.updateStatus(txnId, "FAILED");
            store.setFailureReason(txnId, "TIMEOUT_UNKNOWN");
            log.warn("Simulated timeout: txnId={}", txnId);
            return;
        }
        
        TransactionContext ctx = new TransactionContext(txnId, payer, payee, amount);
        TransactionResult result = pspProcessor.process(ctx);
        
        store.updateStatus(txnId, result.getStatus());
        if (result.getFailureReason() != null) {
            store.setFailureReason(txnId, result.getFailureReason());
        }
        
        monitoringService.addEvent(txnId, result.getStatus());
        log.info("Saga {}: txnId={}, stage={}, reason={}", result.getStatus(), txnId, result.getStage(), result.getFailureReason());
    }

    // Delegate to PSP/NPCI (simulation via TransactionStore)
    private boolean debit(String txnId, String payerUpi, double amount) {
        log.debug("Saga debit payer={} amount={} txnId={}", payerUpi, amount, txnId);
        // Production: npciAdapter.debit(payerUpi, amount, txnId);
        return store.debitPayer(txnId, payerUpi, amount);
    }

    private boolean credit(String txnId, String payeeUpi, double amount) {
        log.debug("Saga credit payee={} amount={} txnId={}", payeeUpi, amount, txnId);
        // Production: npciAdapter.credit(payeeUpi, amount, txnId);
        return store.creditPayee(txnId, payeeUpi, amount);
    }

    /**
     * Idempotent compensation: Reverse debit if not already compensated.
     * Checks failureReason to avoid double compensation.
     */
    private void compensateDebit(String txnId, String payerUpi, double amount) {
        String currentReason = store.getFailureReason(txnId);
        if (currentReason.contains("COMPENSATED")) {
            log.info("Compensation idempotent skip: txnId={}", txnId);
            return;
        }
        
        boolean success = store.compensatePayerDebit(txnId, payerUpi, amount);
        if (success) {
            log.info("Compensation success: txnId={}", txnId);
        } else {
            log.error("Compensation failed: txnId={}", txnId);
        }
    }
}

