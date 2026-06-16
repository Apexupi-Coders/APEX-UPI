package com.hpe.upi.cbs;

import com.hpe.upi.cbs.gateway.CbsGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cbs")
@CrossOrigin(origins = "*")
public class CbsController {

    private final CbsGateway cbsGateway;
    private final JdbcTemplate debitJdbc;
    private final JdbcTemplate creditJdbc;

    public CbsController(CbsGateway cbsGateway,
                         @Qualifier("debitJdbc") JdbcTemplate debitJdbc,
                         @Qualifier("creditJdbc") JdbcTemplate creditJdbc) {
        this.cbsGateway = cbsGateway;
        this.debitJdbc = debitJdbc;
        this.creditJdbc = creditJdbc;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "CBS-Service", "databases", "debit:5433,credit:5434");
    }

    /** Get account balance by VPA */
    @GetMapping("/account/{vpa}/balance")
    public ResponseEntity<Object> getBalance(@PathVariable String vpa) {
        CbsGateway.AccountRecord acc = cbsGateway.getByVpa(vpa);
        if (acc == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Account not found: " + vpa));
        }
        return ResponseEntity.ok(Map.of(
            "vpa", vpa,
            "accountNumber", acc.accountNumber,
            "accountName", acc.accountName,
            "balance", acc.balance,
            "status", acc.status
        ));
    }

    /** List all accounts with balances */
    @GetMapping("/accounts")
    public ResponseEntity<Object> listAccounts() {
        var accounts = cbsGateway.getAllAccounts().values().stream()
            .map(a -> Map.of(
                "vpa", a.vpa,
                "accountNumber", a.accountNumber,
                "accountName", a.accountName,
                "balance", a.balance,
                "status", a.status
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("accounts", accounts));
    }

    /** Transaction history for a VPA from debit ledger */
    @GetMapping("/account/{vpa}/transactions")
    public ResponseEntity<Object> getTransactions(@PathVariable String vpa) {
        try {
            List<Map<String, Object>> debits = debitJdbc.queryForList(
                "SELECT txn_id, amount, status, reversal_reason, created_at, updated_at " +
                "FROM debit_ledger WHERE payer_vpa = ? ORDER BY created_at DESC LIMIT 20", vpa);
            List<Map<String, Object>> credits = creditJdbc.queryForList(
                "SELECT txn_id, amount, status, created_at " +
                "FROM credit_ledger WHERE payee_vpa = ? ORDER BY created_at DESC LIMIT 20", vpa);
            return ResponseEntity.ok(Map.of(
                "vpa", vpa,
                "debits", debits,
                "credits", credits
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** End-of-day reconciliation report */
    @GetMapping("/reconcile/today")
    public ResponseEntity<Object> reconcile() {
        try {
            Map<String, Object> debitSummary = debitJdbc.queryForMap(
                "SELECT COUNT(*) as count, COALESCE(SUM(amount),0) as total " +
                "FROM debit_ledger WHERE DATE(created_at) = CURRENT_DATE AND status='DEBITED'");
            Map<String, Object> creditSummary = creditJdbc.queryForMap(
                "SELECT COUNT(*) as count, COALESCE(SUM(amount),0) as total " +
                "FROM credit_ledger WHERE DATE(created_at) = CURRENT_DATE AND status='CREDITED'");
            Map<String, Object> reversalSummary = debitJdbc.queryForMap(
                "SELECT COUNT(*) as count, COALESCE(SUM(amount),0) as total " +
                "FROM reversal_ledger WHERE DATE(reversed_at) = CURRENT_DATE");

            Object debitTotal   = debitSummary.get("total");
            Object creditTotal  = creditSummary.get("total");
            boolean matched = debitTotal != null && debitTotal.toString().equals(creditTotal != null ? creditTotal.toString() : "");

            return ResponseEntity.ok(Map.of(
                "date", java.time.LocalDate.now().toString(),
                "debits",    Map.of("count", debitSummary.get("count"),   "total", debitTotal),
                "credits",   Map.of("count", creditSummary.get("count"),  "total", creditTotal),
                "reversals", Map.of("count", reversalSummary.get("count"),"total", reversalSummary.get("total")),
                "reconciled", matched,
                "status", matched ? "BALANCED" : "MISMATCH — investigate"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** Debit ledger — all records */
    @GetMapping("/ledger/debit")
    public ResponseEntity<Object> debitLedger() {
        try {
            List<Map<String, Object>> records = debitJdbc.queryForList(
                "SELECT * FROM debit_ledger ORDER BY created_at DESC LIMIT 50");
            return ResponseEntity.ok(Map.of("records", records, "count", records.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** Credit ledger — all records */
    @GetMapping("/ledger/credit")
    public ResponseEntity<Object> creditLedger() {
        try {
            List<Map<String, Object>> records = creditJdbc.queryForList(
                "SELECT * FROM credit_ledger ORDER BY created_at DESC LIMIT 50");
            return ResponseEntity.ok(Map.of("records", records, "count", records.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
