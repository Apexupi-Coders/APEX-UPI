package com.apexupi.psp_switch.controller;

import com.apexupi.psp_switch.reconciliation.ReconciliationScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationScanner scanner;

    /**
     * Manually triggers a reconciliation scan — for demo.
     * In production this runs automatically via @Scheduled.
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> triggerScan() {
        int recovered = scanner.runScan();
        return ResponseEntity.ok(Map.of(
            "status", "SCAN_COMPLETE",
            "transactionsRecovered", recovered,
            "message", recovered > 0
                ? recovered + " stuck transaction(s) detected and recovered"
                : "No stuck transactions found"
        ));
    }
}
