package com.apexupi.psp_switch.reconciliation;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import com.apexupi.psp_switch.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryEngine {

    private final JpaTransactionRepository txnRepo;
    private final MonitoringService monitoringService;

    /**
     * Rule 1: NPCI timed out — no webhook ever arrived.
     * Action: Mark UNKNOWN, escalate to MANUAL_REVIEW.
     */
    public void recoverNpciTimeout(String txnId) {
        TransactionEntity txn = txnRepo.findById(txnId).orElse(null);
        if (txn == null || txn.getState() != TransactionState.NPCI_CALLED) return;

        txn.setState(TransactionState.UNKNOWN);
        txn.setFailureReason("NPCI_TIMEOUT_RECONCILED");
        txnRepo.save(txn);

        monitoringService.addEvent(txnId, "RECOVERY_ACTION_TAKEN");
        monitoringService.addEvent(txnId, "MANUAL_REVIEW");
        log.warn("[RECOVERY] NPCI timeout resolved txnId={} → MANUAL_REVIEW", txnId);
    }

    /**
     * Rule 2: Debit succeeded, credit webhook was lost.
     * Action: Compensate the debit → COMPENSATED (safe rollback).
     */
    public void recoverMissingCredit(String txnId) {
        TransactionEntity txn = txnRepo.findById(txnId).orElse(null);
        if (txn == null || txn.getState() != TransactionState.CBS_DEBIT_SUCCESS) return;

        txn.setState(TransactionState.COMPENSATED);
        txn.setFailureReason("CREDIT_WEBHOOK_LOST_COMPENSATED");
        txnRepo.save(txn);

        monitoringService.addEvent(txnId, "RECOVERY_ACTION_TAKEN");
        monitoringService.addEvent(txnId, "COMPENSATED");
        log.warn("[RECOVERY] Missing credit compensated txnId={} → COMPENSATED", txnId);
    }
}

