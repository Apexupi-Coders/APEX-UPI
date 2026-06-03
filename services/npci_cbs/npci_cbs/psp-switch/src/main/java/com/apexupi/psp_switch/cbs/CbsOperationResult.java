package com.apexupi.psp_switch.cbs;

import lombok.Getter;

/**
 * Immutable result of a CBS operation.
 * Passed to CbsCallbackDispatcher to build the webhook payload.
 */
@Getter
public class CbsOperationResult {

    private final String txnId;
    private final CbsOperationType operation;
    private final boolean success;
    private final String reason; // null on success

    private CbsOperationResult(String txnId, CbsOperationType operation,
                                boolean success, String reason) {
        this.txnId = txnId;
        this.operation = operation;
        this.success = success;
        this.reason = reason;
    }

    public static CbsOperationResult success(String txnId, CbsOperationType op) {
        return new CbsOperationResult(txnId, op, true, null);
    }

    public static CbsOperationResult failure(String txnId, CbsOperationType op, String reason) {
        return new CbsOperationResult(txnId, op, false, reason);
    }

    // Builds the status string WebhookController expects
    // e.g. DEBIT + success → "DEBIT_SUCCESS"
    public String toWebhookStatus() {
        return operation.name() + (success ? "_SUCCESS" : "_FAILED");
    }

    @Override
    public String toString() {
        return "[txnId=" + txnId + "][CBS][" + operation + "] "
                + (success ? "SUCCESS" : "FAILED reason=" + reason);
    }
}
