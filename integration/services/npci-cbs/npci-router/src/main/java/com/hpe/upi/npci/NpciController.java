package com.hpe.upi.npci;

import com.hpe.upi.npci.registry.VpaRegistry;
import com.hpe.upi.npci.service.NpciRoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/npci")
@CrossOrigin(origins = "*")
public class NpciController {

    private final NpciRoutingService svc;
    private final VpaRegistry vpaRegistry;

    public NpciController(NpciRoutingService svc, VpaRegistry vpaRegistry) {
        this.svc = svc;
        this.vpaRegistry = vpaRegistry;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "NPCI-Router");
    }

    @GetMapping("/inflight")
    public Map<String, Object> inflight() {
        return Map.of("inflight", svc.getInFlight(), "history", svc.getHistory());
    }

    /** VPA Registry — list all registered VPAs */
    @GetMapping("/vpa/registry")
    public ResponseEntity<Object> listVpas() {
        var accounts = vpaRegistry.getAllAccounts().values().stream()
            .map(r -> Map.of(
                "vpa", r.vpa,
                "accountNumber", r.accountNumber,
                "accountName", r.accountName,
                "ifsc", r.ifsc,
                "bankCode", r.bankCode,
                "status", r.status
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("accounts", accounts, "total", accounts.size()));
    }

    /** VPA Lookup — resolve a single VPA */
    @GetMapping("/vpa/lookup/{vpa}")
    public ResponseEntity<Object> lookupVpa(@PathVariable String vpa) {
        VpaRegistry.VpaRecord record = vpaRegistry.lookup(vpa);
        if (record == null) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "U001",
                "message", "VPA not registered: " + vpa
            ));
        }
        return ResponseEntity.ok(Map.of(
            "vpa", record.vpa,
            "accountNumber", record.accountNumber,
            "accountName", record.accountName,
            "ifsc", record.ifsc,
            "bankCode", record.bankCode,
            "status", record.status
        ));
    }

    /** Error codes reference */
    @GetMapping("/error-codes")
    public ResponseEntity<Object> errorCodes() {
        var codes = Map.of(
            "U001", "VPA not registered",
            "U002", "Insufficient funds",
            "U003", "Transaction limit exceeded",
            "U004", "Account frozen",
            "U005", "Bank unavailable",
            "U006", "Duplicate transaction",
            "U007", "Self transfer not allowed",
            "U008", "Account dormant",
            "U009", "Invalid VPA format"
        );
        return ResponseEntity.ok(Map.of("errorCodes", codes));
    }
}
