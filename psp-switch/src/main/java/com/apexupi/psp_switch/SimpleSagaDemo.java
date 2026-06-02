
package com.apexupi.psp_switch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@Slf4j
public class SimpleSagaDemo {

    private final Map<String, Double> balances = Map.of("alice@hdfc", 10000.0, "bob@sbi", 5000.0, "eve@icici", 0.0);
    private final AtomicLong compensatedCount = new AtomicLong(0);

    @PostMapping("/simple-saga")
    public String payeeDownDemo(@RequestBody Map<String, String> req) {
        String txnId = "TXN-" + System.currentTimeMillis();
        String payer = req.get("payer");
        String payee = req.get("payee");
        double amount = Double.parseDouble(req.get("amount"));

        log.info("=== SIMPLE SAGA START txnId={} payer={} payee={} amount={} ===", txnId, payer, payee, amount);

        // 1. DEBIT PAYER (always succeeds)
        Double payerBalance = balances.get(payer);
        payerBalance -= amount;
        log.info("✓ DEBIT {} -{} → {} | Payee CBS DOWN SIMULATION", payer, amount, payerBalance);

        // 2. CREDIT PAYEE FAIL (bank down)
        log.error("✗ CREDIT {} FAIL - Payee bank DOWN!", payee);

        // 3. COMPENSATION - REFUND PAYER
        payerBalance += amount;
        compensatedCount.incrementAndGet();
        log.info("🔄 COMPENSATION REFUND {} +{} → {} | Saga Complete!", payer, amount, payerBalance);

        log.info("📊 STATS: Compensations={} | Payer Protected!", compensatedCount.get());
        log.info("=== SIMPLE SAGA END txnId={} STATUS=COMPENSATED ===", txnId);

        return String.format("TXN %s COMPENSATED - Payee down, payer refunded!", txnId);
    }

    @PostMapping("/stats")
    public String stats() {
        return "Compensations (payee down cases): " + compensatedCount.get();
    }
}

