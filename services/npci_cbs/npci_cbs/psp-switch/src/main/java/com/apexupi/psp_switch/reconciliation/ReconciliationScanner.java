package com.apexupi.psp_switch.reconciliation;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import com.apexupi.psp_switch.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScanner {

    private final JpaTransactionRepository txnRepo;
    private final RecoveryEngine recoveryEngine;
    private final MonitoringService monitoringService;

    // Runs automatically every 60s in production
    @Scheduled(fixedDelay = 60000)
    public void scheduledScan() {
        log.info("[RECONCILIATION] Scheduled scan running...");
        runScan();
    }

    // Called manually via POST /reconciliation/scan for demo
    public int runScan() {
        int recovered = 0;

        // Rule 1: NPCI_CALLED stuck > 5s (5s for demo; use 30s in prod)
        LocalDateTime npciCutoff = LocalDateTime.now().minusSeconds(5);
        List<TransactionEntity> stuckNpci = txnRepo.findByState(TransactionState.NPCI_CALLED)
            .stream()
            .filter(t -> t.getCreatedAt().isBefore(npciCutoff))
            .toList();

        for (TransactionEntity txn : stuckNpci) {
            log.warn("[RECONCILIATION] Rule 1 — NPCI_TIMEOUT detected txnId={}", txn.getTxnId());
            monitoringService.addEvent(txn.getTxnId(), "RECONCILIATION_DETECTED_NPCI_TIMEOUT");
            recoveryEngine.recoverNpciTimeout(txn.getTxnId());
            recovered++;
        }

        // Rule 2: CBS_DEBIT_SUCCESS stuck > 5s (credit webhook lost)
        LocalDateTime creditCutoff = LocalDateTime.now().minusSeconds(5);
        List<TransactionEntity> stuckCredit = txnRepo.findByState(TransactionState.CBS_DEBIT_SUCCESS)
            .stream()
            .filter(t -> t.getCreatedAt().isBefore(creditCutoff))
            .toList();

        for (TransactionEntity txn : stuckCredit) {
            log.warn("[RECONCILIATION] Rule 2 — MISSING_CREDIT detected txnId={}", txn.getTxnId());
            monitoringService.addEvent(txn.getTxnId(), "RECONCILIATION_DETECTED_MISSING_CREDIT");
            recoveryEngine.recoverMissingCredit(txn.getTxnId());
            recovered++;
        }

        log.info("[RECONCILIATION] Scan complete. Recovered={}", recovered);
        return recovered;
    }
}

