package com.apexupi.psp_switch.cbs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Operation-level idempotency store for CBS Engine.
 *
 * Key:   txnId + ":" + operationType   (e.g. "abc-123:DEBIT")
 * Value: the CbsOperationResult of the first execution
 *
 * On retry: return the stored result immediately without re-executing.
 *
 * Production equivalent: Redis with TTL per operation key.
 */
@Component
@Slf4j
public class CbsIdempotencyStore {

    private final ConcurrentHashMap<String, CbsOperationResult> store = new ConcurrentHashMap<>();

    private String key(String txnId, CbsOperationType op) {
        return txnId + ":" + op.name();
    }

    /**
     * Check if this operation was already executed.
     * Returns the stored result if yes, null if this is a new operation.
     */
    public CbsOperationResult getIfExists(String txnId, CbsOperationType op) {
        String k = key(txnId, op);
        CbsOperationResult existing = store.get(k);
        if (existing != null) {
            log.info("[CBS][IDEMPOTENCY] Duplicate operation detected — returning stored result. key={}", k);
        }
        return existing;
    }

    /**
     * Record the result of a completed operation.
     * Must be called exactly once after execution.
     */
    public void record(CbsOperationResult result) {
        String k = key(result.getTxnId(), result.getOperation());
        store.put(k, result);
        log.info("[CBS][IDEMPOTENCY] Recorded result key={} status={}", k, result.toWebhookStatus());
    }

    public boolean hasRecord(String txnId, CbsOperationType op) {
        return store.containsKey(key(txnId, op));
    }
}
