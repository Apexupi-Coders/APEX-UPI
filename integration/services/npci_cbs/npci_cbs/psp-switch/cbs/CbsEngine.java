package com.apexupi.psp_switch.cbs;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CBS Execution Engine — the core banking simulation.
 *
 * Responsibilities:
 *  1. Validate account state before execution
 *  2. Execute debit / credit / reversal atomically
 *  3. Enforce idempotency (no double-debit/credit on retry)
 *  4. Write to CBS ledger for audit trail
 *  5. Dispatch async webhook callback to orchestrator
 *
 * Called by: CBSAdapter (which is called by TransactionOrchestrator)
 * Calls back: POST /webhook/cbs/{txnId} via CbsCallbackDispatcher
 *
 * Design: synchronous execution, asynchronous response.
 * This mirrors real CBS behavior — the bank processes immediately
 * but the PSP only learns the result via async callback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CbsEngine {

    private final CbsAccountStore accountStore;
    private final CbsIdempotencyStore idempotencyStore;
    private final CbsLedger ledger;
    private final CbsCallbackDispatcher dispatcher;
    private final JpaTransactionRepository transactionRepo;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API — called by CBSAdapter
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Debit the payer account.
     * Reads payer UPI from TransactionEntity.
     * Dispatches async callback with DEBIT_SUCCESS or DEBIT_FAILED.
     */
    public void executeDebit(String txnId) {
        log.info("[txnId={}][CBS][DEBIT] Received debit request", txnId);

        // Idempotency check — if already executed, re-dispatch the same result
        CbsOperationResult existing = idempotencyStore.getIfExists(txnId, CbsOperationType.DEBIT);
        if (existing != null) {
            log.warn("[txnId={}][CBS][DEBIT] Duplicate — re-dispatching stored result: {}",
                    txnId, existing.toWebhookStatus());
            dispatcher.dispatchAsync(existing);
            return;
        }

        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn == null) {
            log.error("[txnId={}][CBS][DEBIT] Transaction not found in DB", txnId);
            CbsOperationResult result = CbsOperationResult.failure(txnId, CbsOperationType.DEBIT, "TXN_NOT_FOUND");
            finalize(result, "N/A", txn != null ? txn.getAmount() : 0);
            return;
        }

        String payerUpi = txn.getPayer();
        double amount   = txn.getAmount();

        CbsOperationResult result = performDebit(txnId, payerUpi, amount);
        finalize(result, payerUpi, amount);
    }

    /**
     * Credit the payee account.
     * Reads payee UPI from TransactionEntity.
     * Dispatches async callback with CREDIT_SUCCESS or CREDIT_FAILED.
     */
    public void executeCredit(String txnId) {
        log.info("[txnId={}][CBS][CREDIT] Received credit request", txnId);

        CbsOperationResult existing = idempotencyStore.getIfExists(txnId, CbsOperationType.CREDIT);
        if (existing != null) {
            log.warn("[txnId={}][CBS][CREDIT] Duplicate — re-dispatching stored result: {}",
                    txnId, existing.toWebhookStatus());
            dispatcher.dispatchAsync(existing);
            return;
        }

        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn == null) {
            log.error("[txnId={}][CBS][CREDIT] Transaction not found in DB", txnId);
            CbsOperationResult result = CbsOperationResult.failure(txnId, CbsOperationType.CREDIT, "TXN_NOT_FOUND");
            finalize(result, "N/A", 0);
            return;
        }

        String payeeUpi = txn.getPayee();
        double amount   = txn.getAmount();

        CbsOperationResult result = performCredit(txnId, payeeUpi, amount);
        finalize(result, payeeUpi, amount);
    }

    /**
     * Reverse a previous debit (Saga compensation).
     * Credits back the payer account for the same amount.
     * Idempotent — safe to call multiple times.
     */
    public void executeReversal(String txnId) {
        log.info("[txnId={}][CBS][REVERSAL] Received reversal request", txnId);

        CbsOperationResult existing = idempotencyStore.getIfExists(txnId, CbsOperationType.REVERSAL);
        if (existing != null) {
            log.warn("[txnId={}][CBS][REVERSAL] Duplicate — re-dispatching stored result: {}",
                    txnId, existing.toWebhookStatus());
            dispatcher.dispatchAsync(existing);
            return;
        }

        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn == null) {
            log.error("[txnId={}][CBS][REVERSAL] Transaction not found in DB", txnId);
            CbsOperationResult result = CbsOperationResult.failure(txnId, CbsOperationType.REVERSAL, "TXN_NOT_FOUND");
            finalize(result, "N/A", 0);
            return;
        }

        // Reversal credits money back to the payer
        String payerUpi = txn.getPayer();
        double amount   = txn.getAmount();

        CbsOperationResult result = performReversal(txnId, payerUpi, amount);
        finalize(result, payerUpi, amount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE EXECUTION LOGIC
    // ─────────────────────────────────────────────────────────────────────────

    private CbsOperationResult performDebit(String txnId, String upiId, double amount) {
        // 1. Account existence check
        if (!accountStore.exists(upiId)) {
            log.warn("[txnId={}][CBS][DEBIT] failed reason=ACCOUNT_NOT_FOUND upiId={}", txnId, upiId);
            return CbsOperationResult.failure(txnId, CbsOperationType.DEBIT, "ACCOUNT_NOT_FOUND");
        }

        // 2. Balance check + debit (atomic via per-account lock)
        boolean debited = accountStore.debit(upiId, amount);
        if (!debited) {
            double balance = accountStore.getBalance(upiId);
            log.warn("[txnId={}][CBS][DEBIT] failed reason=INSUFFICIENT_FUNDS upiId={} balance={} required={}",
                    txnId, upiId, balance, amount);
            return CbsOperationResult.failure(txnId, CbsOperationType.DEBIT, "INSUFFICIENT_FUNDS");
        }

        log.info("[txnId={}][CBS][DEBIT] success upiId={} amount={} newBalance={}",
                txnId, upiId, amount, accountStore.getBalance(upiId));
        return CbsOperationResult.success(txnId, CbsOperationType.DEBIT);
    }

    private CbsOperationResult performCredit(String txnId, String upiId, double amount) {
        if (!accountStore.exists(upiId)) {
            log.warn("[txnId={}][CBS][CREDIT] failed reason=ACCOUNT_NOT_FOUND upiId={}", txnId, upiId);
            return CbsOperationResult.failure(txnId, CbsOperationType.CREDIT, "ACCOUNT_NOT_FOUND");
        }

        accountStore.credit(upiId, amount);

        log.info("[txnId={}][CBS][CREDIT] success upiId={} amount={} newBalance={}",
                txnId, upiId, amount, accountStore.getBalance(upiId));
        return CbsOperationResult.success(txnId, CbsOperationType.CREDIT);
    }

    private CbsOperationResult performReversal(String txnId, String upiId, double amount) {
        if (!accountStore.exists(upiId)) {
            log.warn("[txnId={}][CBS][REVERSAL] failed reason=ACCOUNT_NOT_FOUND upiId={}", txnId, upiId);
            return CbsOperationResult.failure(txnId, CbsOperationType.REVERSAL, "ACCOUNT_NOT_FOUND");
        }

        // Reversal = credit back to payer (undoes the debit)
        accountStore.credit(upiId, amount);

        log.info("[txnId={}][CBS][REVERSAL] success upiId={} amount={} newBalance={}",
                txnId, upiId, amount, accountStore.getBalance(upiId));
        return CbsOperationResult.success(txnId, CbsOperationType.REVERSAL);
    }

    /**
     * Common finalization: record in ledger, record idempotency, dispatch callback.
     * Called for every operation regardless of success/failure.
     */
    private void finalize(CbsOperationResult result, String upiId, double amount) {
        // Write to audit ledger
        ledger.record(result.getTxnId(), result.getOperation(), upiId, amount,
                result.isSuccess(), result.getReason());

        // Record idempotency so retries return same result
        idempotencyStore.record(result);

        // Dispatch async webhook to PSP orchestrator
        dispatcher.dispatchAsync(result);
    }
}