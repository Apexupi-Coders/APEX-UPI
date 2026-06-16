package com.apexupi.operations.api;

import com.apexupi.operations.model.TransactionJourneyDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Read-only transaction queries for the dashboard.
 */
@RestController
public class TransactionsReadOnlyController {

    @GetMapping("/api/v1/ops/transactions/{tid}/journey")
    public TransactionJourneyDto journeyByTid(@PathVariable String tid) {
        // Placeholder. Later: read from PostgreSQL state/ledger/audit tables.
        TransactionJourneyDto dto = new TransactionJourneyDto();
        dto.setTxnId(tid);
        dto.setCorrelationId(null);
        dto.setApprovalRefNo(null);
        dto.setAmount(null);
        dto.setPayer(null);
        dto.setPayee(null);
        dto.setStateChanges(Collections.emptyList());
        return dto;
    }

    @GetMapping("/api/v1/ops/transactions/search")
    public Map<String, Object> search(
            @RequestParam(required = false) String tr,
            @RequestParam(required = false) String pa) {
        // Placeholder. Later: composite key lookup.
        return Map.of(
                "timestamp", Instant.now().toString(),
                "results", Collections.emptyList()
        );
    }
}

