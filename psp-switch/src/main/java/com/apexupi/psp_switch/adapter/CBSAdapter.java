package com.apexupi.psp_switch.adapter;

import com.apexupi.psp_switch.cbs.CbsEngine;
import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * CBSAdapter — thin bridge between TransactionOrchestrator and CbsEngine.
 *
 * Responsibilities:
 *  - Set JPA transaction state to PENDING before handing off
 *  - Delegate actual execution to CbsEngine
 *  - CbsEngine handles idempotency, account ops, ledger, and async callback
 *
 * No direct account logic here. No RestTemplate here.
 * All of that lives in CbsEngine and CbsCallbackDispatcher.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CBSAdapter {

    private final JpaTransactionRepository transactionRepo;
    private final CbsEngine cbsEngine;

    public void debit(String txnId) {
        log.info("[txnId={}][CBS_ADAPTER] Initiating debit", txnId);
        updateState(txnId, TransactionState.CBS_DEBIT_PENDING);
        cbsEngine.executeDebit(txnId);
    }

    public void credit(String txnId) {
        log.info("[txnId={}][CBS_ADAPTER] Initiating credit", txnId);
        updateState(txnId, TransactionState.CBS_CREDIT_PENDING);
        cbsEngine.executeCredit(txnId);
    }

    public void reversal(String txnId) {
        log.info("[txnId={}][CBS_ADAPTER] Initiating reversal", txnId);
        // Reversal keeps the same COMPENSATED state — orchestrator sets it
        cbsEngine.executeReversal(txnId);
    }

    private void updateState(String txnId, TransactionState state) {
        transactionRepo.findById(txnId).ifPresent(txn -> {
            txn.setState(state);
            txn.setUpdatedAt(LocalDateTime.now());
            transactionRepo.save(txn);
        });
    }
}