package com.apexupi.psp_switch.orchestrator;

import com.apexupi.psp_switch.adapter.CBSAdapter;
import com.apexupi.psp_switch.adapter.NPCIAdapter;
import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import com.apexupi.psp_switch.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionOrchestrator {

    private final JpaTransactionRepository transactionRepo;
    private final NPCIAdapter npciAdapter;
    private final CBSAdapter cbsAdapter;
    private final MonitoringService monitoringService;

    /**
     * Entry point — called by PaymentService after saving the transaction.
     * Moves state to SUBMITTED and kicks off async NPCI validation.
     */
    public void start(String txnId) {
        MDC.put("txnId", txnId);
        log.info("[ORCHESTRATOR] start txnId={}", txnId);
        monitoringService.addEvent(txnId, "ORCHESTRATOR_START");

        TransactionEntity txn = transactionRepo.findById(txnId).orElseThrow();
        txn.setState(TransactionState.SUBMITTED);
        txn.setUpdatedAt(java.time.LocalDateTime.now());
        transactionRepo.save(txn);

        npciAdapter.submitTransaction(txnId);
    }

    /**
     * Called by WebhookController when NPCI responds.
     * If NPCI succeeded → kick off CBS debit.
     * If NPCI failed → already marked FAILED by webhook controller.
     */
    @Async
    public void onNpciCallback(String txnId) {
        MDC.put("txnId", txnId);
        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn == null) {
            log.error("[ORCHESTRATOR] onNpciCallback — txn not found: {}", txnId);
            return;
        }

        log.info("[ORCHESTRATOR] onNpciCallback txnId={} state={}", txnId, txn.getState());

        if (txn.getState() == TransactionState.NPCI_SUCCESS) {
            monitoringService.addEvent(txnId, "NPCI_SUCCESS_RECEIVED");
            cbsAdapter.debit(txnId);
        } else {
            monitoringService.addEvent(txnId, "NPCI_FAILED_RECEIVED");
            log.warn("[ORCHESTRATOR] NPCI failed for txnId={}", txnId);
        }
    }

    /**
     * Called by WebhookController when CBS responds (debit OR credit).
     *
     * State machine:
     *   CBS_DEBIT_SUCCESS  → trigger credit
     *   SUCCESS            → final, log and done
     *   COMPENSATED        → credit failed, debit was reversed by webhook controller
     *   FAILED             → debit failed
     */
    @Async
    public void onCbsCallback(String txnId) {
        MDC.put("txnId", txnId);
        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn == null) {
            log.error("[ORCHESTRATOR] onCbsCallback — txn not found: {}", txnId);
            return;
        }

        log.info("[ORCHESTRATOR] onCbsCallback txnId={} state={}", txnId, txn.getState());
        monitoringService.addEvent(txnId, "CBS_CALLBACK_" + txn.getState().name());

        switch (txn.getState()) {
            case CBS_DEBIT_SUCCESS -> {
                log.info("[ORCHESTRATOR] Debit succeeded — initiating credit txnId={}", txnId);
                cbsAdapter.credit(txnId);
            }
            case SUCCESS -> {
                monitoringService.addEvent(txnId, "SUCCESS");
                log.info("[ORCHESTRATOR] final=SUCCESS txnId={}", txnId);
            }
            case COMPENSATED -> {
                monitoringService.addEvent(txnId, "COMPENSATED");
                log.warn("[ORCHESTRATOR] final=COMPENSATED txnId={}", txnId);
            }
            case FAILED -> {
                monitoringService.addEvent(txnId, "FAILED");
                log.warn("[ORCHESTRATOR] final=FAILED txnId={}", txnId);
            }
            default -> log.warn("[ORCHESTRATOR] unexpected state={} txnId={}", txn.getState(), txnId);
        }
    }

    /**
     * Called by ReconciliationScanner for stuck transactions.
     */
    public void timeout(String txnId) {
        MDC.put("txnId", txnId);
        log.warn("[ORCHESTRATOR] timeout txnId={}", txnId);
        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn != null) {
            txn.setState(TransactionState.UNKNOWN);
            txn.setUpdatedAt(java.time.LocalDateTime.now());
            transactionRepo.save(txn);
            monitoringService.addEvent(txnId, "TIMEOUT_UNKNOWN");
        }
    }
}