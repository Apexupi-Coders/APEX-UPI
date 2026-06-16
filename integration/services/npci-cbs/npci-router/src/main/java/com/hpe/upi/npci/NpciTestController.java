package com.hpe.upi.npci;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.upi.npci.registry.VpaRegistry;
import com.hpe.upi.npci.service.NpciRoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NpciTestController — test entry point that simulates what PSP Switch would publish.
 *
 * Since PSP Switch is not part of this project (it belongs to another team),
 * this controller lets you inject transactions directly into the NPCI layer
 * exactly as a real PSP would via Kafka.
 *
 * In real UPI:
 *   PSP Switch publishes to upi.transactions.initiated over ISO 8583 / leased line
 *
 * In this demo:
 *   POST /api/npci/test/pay publishes to upi.transactions.initiated over Kafka
 *   — functionally identical from NPCI's perspective
 */
@RestController
@RequestMapping("/api/npci/test")
@CrossOrigin(origins = "*")
public class NpciTestController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final VpaRegistry vpaRegistry;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public NpciTestController(KafkaTemplate<String, String> kafkaTemplate,
                               VpaRegistry vpaRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.vpaRegistry = vpaRegistry;
    }

    /**
     * Simulates a PSP sending a payment request to NPCI.
     * Publishes directly to upi.transactions.initiated Kafka topic.
     *
     * Real UPI equivalent:
     *   PSP → ISO 8583 message → NPCI leased line → NPCI Router
     *
     * This demo equivalent:
     *   POST /api/npci/test/pay → JSON → Kafka → NPCI Router
     */
    @PostMapping("/pay")
    public ResponseEntity<Object> simulatePspPayment(@RequestBody Map<String, String> request) {
        try {
            String txnId       = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            String payerVpa    = request.getOrDefault("payerVpa", "alice@sbi");
            String payeeVpa    = request.getOrDefault("payeeVpa", "bob@hdfc");
            String amountStr   = request.getOrDefault("amount", "100");
            boolean simFail    = Boolean.parseBoolean(request.getOrDefault("simulateFailure", "false"));

            // Extract bank from VPA handle (e.g. alice@sbi → SBI)
            String payerBank = payerVpa.contains("@")
                ? payerVpa.split("@")[1].toUpperCase() : "UNKNOWN";
            String payeeBank = payeeVpa.contains("@")
                ? payeeVpa.split("@")[1].toUpperCase() : "UNKNOWN";

            // Build the Kafka payload — exactly what PSP Switch would send
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("txnId",           txnId);
            payload.put("rrn",             "RRN" + txnId.substring(3, 11));
            payload.put("payerVpa",        payerVpa);
            payload.put("payeeVpa",        payeeVpa);
            payload.put("payerBank",       payerBank);
            payload.put("payeeBank",       payeeBank);
            payload.put("amount",          amountStr);
            payload.put("status",          "INITIATED");
            payload.put("simulateFailure", simFail);
            payload.put("createdAt",       Instant.now().toString());
            payload.put("source",          "NPCI-TEST-CONTROLLER");

            // Publish to Kafka — NPCI Router will consume this
            kafkaTemplate.send("upi.transactions.initiated", txnId,
                mapper.writeValueAsString(payload));

            // Lookup VPA info for response
            VpaRegistry.VpaRecord payer = vpaRegistry.lookup(payerVpa);
            VpaRegistry.VpaRecord payee = vpaRegistry.lookup(payeeVpa);

            return ResponseEntity.ok(Map.of(
                "txnId",       txnId,
                "rrn",         payload.get("rrn"),
                "status",      "INITIATED",
                "payerVpa",    payerVpa,
                "payerName",   payer != null ? payer.accountName : "Unknown",
                "payeeVpa",    payeeVpa,
                "payeeName",   payee != null ? payee.accountName : "Unknown",
                "amount",      amountStr,
                "message",     "Transaction published to NPCI via Kafka. PSP role simulated.",
                "note",        "PSP Switch is not part of this project. This endpoint simulates PSP → Kafka → NPCI flow."
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** Quick health check for test controller */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "role",   "PSP simulator — publishes to upi.transactions.initiated",
            "note",   "PSP Switch belongs to another team. This simulates their Kafka publish."
        );
    }

    /** List all available test VPAs */
    @GetMapping("/accounts")
    public ResponseEntity<Object> availableAccounts() {
        var accounts = vpaRegistry.getAllAccounts().values().stream()
            .map(r -> Map.of(
                "vpa",     r.vpa,
                "name",    r.accountName,
                "bank",    r.bankCode,
                "status",  r.status
            ))
            .toList();
        return ResponseEntity.ok(Map.of(
            "accounts", accounts,
            "tip", "Use these VPAs in your test/pay requests"
        ));
    }
}
