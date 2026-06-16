package com.apexupi.psp_switch.controller;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import com.apexupi.psp_switch.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@Slf4j
public class ReconciliationDemoController {

    private final JpaTransactionRepository txnRepo;
    private final MonitoringService monitoringService;

    /**
     * Simulates a txn stuck at NPCI_CALLED (webhook never arrived).
     * Reconciliation scanner will detect this after 30s and mark MANUAL_REVIEW.
     */
    @PostMapping("/stuck/npci-timeout")
    public ResponseEntity<Map<String, String>> simulateNpciTimeout() {
        String txnId = UUID.randomUUID().toString();

        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(txnId);
        txn.setPayer("alice@hdfc");
        txn.setPayee("bob@sbi");
        txn.setAmount(250.0);
        txn.setIdempotencyKey("demo-stuck-npci-" + txnId);
        txn.setState(TransactionState.NPCI_CALLED); // deliberately stuck here
        txnRepo.save(txn);

        monitoringService.addEvent(txnId, "ORCHESTRATOR_START");
        monitoringService.addEvent(txnId, "NPCI_SUBMITTED");
        monitoringService.addEvent(txnId, "NPCI_WAITING_WEBHOOK"); // webhook never comes

        log.warn("[DEMO] Simulated NPCI_TIMEOUT stuck txnId={}", txnId);
        return ResponseEntity.ok(Map.of(
            "txnId", txnId,
            "state", "NPCI_CALLED",
            "scenario", "NPCI_TIMEOUT",
            "hint", "Call POST /reconciliation/scan after 5s to recover"
        ));
    }

    /**
     * Simulates a txn where CBS debit succeeded but credit webhook was lost.
     * Reconciliation will detect and compensate.
     */
    @PostMapping("/stuck/missing-credit")
    public ResponseEntity<Map<String, String>> simulateMissingCredit() {
        String txnId = UUID.randomUUID().toString();

        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(txnId);
        txn.setPayer("alice@hdfc");
        txn.setPayee("bob@sbi");
        txn.setAmount(300.0);
        txn.setIdempotencyKey("demo-stuck-credit-" + txnId);
        txn.setState(TransactionState.CBS_DEBIT_SUCCESS); // debit done, credit missing
        txnRepo.save(txn);

        monitoringService.addEvent(txnId, "ORCHESTRATOR_START");
        monitoringService.addEvent(txnId, "NPCI_SUCCESS");
        monitoringService.addEvent(txnId, "CBS_DEBIT_SUCCESS");
        monitoringService.addEvent(txnId, "CBS_CREDIT_WEBHOOK_LOST"); // webhook dropped

        log.warn("[DEMO] Simulated MISSING_CREDIT stuck txnId={}", txnId);
        return ResponseEntity.ok(Map.of(
            "txnId", txnId,
            "state", "CBS_DEBIT_SUCCESS",
            "scenario", "MISSING_CREDIT",
            "hint", "Call POST /reconciliation/scan after 5s to compensate"
        ));
    }
}