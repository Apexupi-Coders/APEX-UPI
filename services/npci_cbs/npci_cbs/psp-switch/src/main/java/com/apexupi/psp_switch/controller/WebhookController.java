package com.apexupi.psp_switch.controller;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.orchestrator.TransactionOrchestrator;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import com.apexupi.psp_switch.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/webhook")
public class WebhookController {

    private final JpaTransactionRepository transactionRepo;
    private final TransactionOrchestrator orchestrator;
   private final MonitoringService monitoringService;

    @PostMapping("/npci/{txnId}")
    public ResponseEntity<String> npciCallback(
            @PathVariable String txnId,
            @RequestBody Map<String, String> payload) {

        String status = payload.get("status");
        log.info("[WEBHOOK] /npci txnId={} status={}", txnId, status);

        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn == null) return ResponseEntity.badRequest().body("Txn not found: " + txnId);

        if ("SUCCESS".equals(status)) {
            txn.setState(TransactionState.NPCI_SUCCESS);
        } else {
            txn.setState(TransactionState.FAILED);
            txn.setFailureReason("NPCI_CALLBACK_FAILED");
        }
        txn.setUpdatedAt(LocalDateTime.now());
        transactionRepo.save(txn);

        monitoringService.addEvent(txnId, "WEBHOOK_NPCI_" + status);
        orchestrator.onNpciCallback(txnId);
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/cbs/{txnId}")
    public ResponseEntity<String> cbsCallback(
            @PathVariable String txnId,
            @RequestBody Map<String, String> payload) {

        String status = payload.get("status");
        log.info("[WEBHOOK] /cbs txnId={} status={}", txnId, status);

        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn == null) return ResponseEntity.badRequest().body("Txn not found: " + txnId);

        String reason = payload.getOrDefault("reason", null);
        log.info("[WEBHOOK] /cbs txnId={} status={} reason={}", txnId, status, reason);

        switch (status) {
            case "DEBIT_SUCCESS"    -> txn.setState(TransactionState.CBS_DEBIT_SUCCESS);
            case "DEBIT_FAILED"     -> {
                txn.setState(TransactionState.FAILED);
                txn.setFailureReason("CBS_DEBIT_FAILED" + (reason != null ? ":" + reason : ""));
            }
            case "CREDIT_SUCCESS"   -> txn.setState(TransactionState.SUCCESS);
            case "CREDIT_FAILED"    -> {
                txn.setState(TransactionState.COMPENSATED);
                txn.setFailureReason("CBS_CREDIT_FAILED:" + reason);
            }
            case "REVERSAL_SUCCESS" -> {
                txn.setState(TransactionState.COMPENSATED);
                txn.setFailureReason("REVERSAL_COMPLETE");
            }
            case "REVERSAL_FAILED"  -> {
                txn.setState(TransactionState.MANUAL_REVIEW);
                txn.setFailureReason("REVERSAL_FAILED:" + reason);
            }
            default -> {
                log.warn("[WEBHOOK] Unknown CBS status={}", status);
                return ResponseEntity.badRequest().body("Unknown status: " + status);
            }
        }

        txn.setUpdatedAt(LocalDateTime.now());
        transactionRepo.save(txn);
        monitoringService.addEvent(txnId, "WEBHOOK_CBS_" + status);
        orchestrator.onCbsCallback(txnId);
        return ResponseEntity.ok("OK");

    }
}