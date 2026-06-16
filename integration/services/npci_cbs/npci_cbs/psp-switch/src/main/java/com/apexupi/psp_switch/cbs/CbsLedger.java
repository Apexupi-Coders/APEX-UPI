package com.apexupi.psp_switch.cbs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Append-only CBS ledger.
 * Every operation (debit, credit, reversal) writes one entry.
 * Provides full audit trail for a transaction.
 *
 * Thread-safe: CopyOnWriteArrayList — reads dominate, writes are rare.
 * Production equivalent: ledger table in core banking DB.
 */
@Component
@Slf4j
public class CbsLedger {

    public record LedgerRecord(
            String txnId,
            CbsOperationType operation,
            String upiId,
            double amount,
            boolean success,
            String reason,
            LocalDateTime timestamp
    ) {}

    private final CopyOnWriteArrayList<LedgerRecord> entries = new CopyOnWriteArrayList<>();

    public void record(String txnId, CbsOperationType operation,
                       String upiId, double amount, boolean success, String reason) {
        LedgerRecord entry = new LedgerRecord(
                txnId, operation, upiId, amount, success, reason, LocalDateTime.now()
        );
        entries.add(entry);
        log.info("[CBS][LEDGER] txnId={} op={} upiId={} amount={} success={} reason={}",
                txnId, operation, upiId, amount, success, reason);
    }

    public List<LedgerRecord> getByTxnId(String txnId) {
        return entries.stream()
                .filter(e -> e.txnId().equals(txnId))
                .collect(Collectors.toList());
    }

    public List<LedgerRecord> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
