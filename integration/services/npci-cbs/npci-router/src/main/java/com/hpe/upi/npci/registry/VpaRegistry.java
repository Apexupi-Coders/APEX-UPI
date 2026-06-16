package com.hpe.upi.npci.registry;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VpaRegistry — maps VPA handles to real bank account details.
 *
 * In real UPI, this lookup hits NPCI's central VPA resolution service.
 * For this demo, we maintain an in-memory registry with pre-seeded accounts.
 *
 * Each entry contains:
 *  - accountNumber : actual bank account number
 *  - accountName   : registered account holder name
 *  - ifsc          : bank IFSC code
 *  - bankCode      : internal bank routing code
 *  - status        : ACTIVE | FROZEN | DORMANT
 */
@Component
public class VpaRegistry {

    public static class VpaRecord {
        public final String vpa;
        public final String accountNumber;
        public final String accountName;
        public final String ifsc;
        public final String bankCode;
        public final String status; // ACTIVE, FROZEN, DORMANT

        public VpaRecord(String vpa, String accountNumber, String accountName,
                         String ifsc, String bankCode, String status) {
            this.vpa = vpa;
            this.accountNumber = accountNumber;
            this.accountName = accountName;
            this.ifsc = ifsc;
            this.bankCode = bankCode;
            this.status = status;
        }

        public boolean isActive() { return "ACTIVE".equals(status); }
        public boolean isFrozen() { return "FROZEN".equals(status); }
        public boolean isDormant() { return "DORMANT".equals(status); }
    }

    private final Map<String, VpaRecord> registry = new ConcurrentHashMap<>();

    public VpaRegistry() {
        // Pre-seeded demo accounts
        register("alice@sbi",    "10001234567890", "Alice Sharma",   "SBIN0001", "SBI",    "ACTIVE");
        register("bob@hdfc",     "20009876543210", "Bob Kumar",      "HDFC0001", "HDFC",   "ACTIVE");
        register("charlie@icici","30001122334455", "Charlie Mehta",  "ICIC0001", "ICICI",  "ACTIVE");
        register("diana@axis",   "40005566778899", "Diana Patel",    "UTIB0001", "AXIS",   "ACTIVE");
        register("eve@kotak",    "50009988776655", "Eve Nair",       "KKBK0001", "KOTAK",  "ACTIVE");
        register("frozen@hdfc",  "20001111111111", "Frozen Account", "HDFC0001", "HDFC",   "FROZEN");
        register("dormant@sbi",  "10002222222222", "Dormant Account","SBIN0001", "SBI",    "DORMANT");
        register("merchant@ybl", "60003344556677", "Test Merchant",  "YESB0001", "YBL",    "ACTIVE");
    }

    private void register(String vpa, String accountNumber, String accountName,
                          String ifsc, String bankCode, String status) {
        registry.put(vpa.toLowerCase(), new VpaRecord(vpa, accountNumber, accountName, ifsc, bankCode, status));
    }

    public VpaRecord lookup(String vpa) {
        if (vpa == null) return null;
        return registry.get(vpa.toLowerCase());
    }

    public boolean exists(String vpa) {
        return lookup(vpa) != null;
    }

    public Map<String, VpaRecord> getAllAccounts() {
        return new HashMap<>(registry);
    }
}
