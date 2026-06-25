package com.hpe.upi.cbs.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CbsGateway — protocol adapter between NPCI Router and CBS databases.
 *
 * In real banking, this gateway:
 *  - Translates to ISO 20022 message format
 *  - Checks account balance before debiting
 *  - Validates account existence and status
 *  - Generates CBS-specific transaction IDs (separate from UPI txnId)
 *  - Handles CBS timeout and retry separately from NPCI timeout
 *
 * For this demo, it maintains in-memory account balances
 * seeded to match the VPA registry.
 */
@Component
public class CbsGateway {

    public static class AccountRecord {
        public final String accountNumber;
        public final String vpa;
        public final String accountName;
        public BigDecimal balance;
        public String status; // ACTIVE, FROZEN, DORMANT

        public AccountRecord(String accountNumber, String vpa, String accountName,
                             BigDecimal balance, String status) {
            this.accountNumber = accountNumber;
            this.vpa = vpa;
            this.accountName = accountName;
            this.balance = balance;
            this.status = status;
        }
    }

    // In-memory account store keyed by accountNumber
    private final Map<String, AccountRecord> accounts = new ConcurrentHashMap<>();
    // VPA → accountNumber index
    private final Map<String, String> vpaIndex = new ConcurrentHashMap<>();

    public CbsGateway() {
        // Seed accounts matching VPA registry
        addAccount("10001234567890", "alice@sbi",     "Alice Sharma",   new BigDecimal("10000.00"), "ACTIVE");
        addAccount("20009876543210", "bob@hdfc",      "Bob Kumar",      new BigDecimal("5000.00"),  "ACTIVE");
        addAccount("30001122334455", "charlie@icici", "Charlie Mehta",  new BigDecimal("2000.00"),  "ACTIVE");
        addAccount("40005566778899", "diana@axis",    "Diana Patel",    new BigDecimal("8000.00"),  "ACTIVE");
        addAccount("50009988776655", "eve@kotak",     "Eve Nair",       new BigDecimal("15000.00"), "ACTIVE");
        addAccount("60003344556677", "merchant@ybl",  "Test Merchant",  new BigDecimal("50000.00"), "ACTIVE");
        addAccount("20001111111111", "frozen@hdfc",   "Frozen Account", new BigDecimal("1000.00"),  "FROZEN");
        addAccount("10002222222222", "dormant@sbi",   "Dormant Account",new BigDecimal("500.00"),   "DORMANT");
    }

    private void addAccount(String accNo, String vpa, String name, BigDecimal balance, String status) {
        accounts.put(accNo, new AccountRecord(accNo, vpa, name, balance, status));
        vpaIndex.put(vpa.toLowerCase(), accNo);
    }

    public AccountRecord getByVpa(String vpa) {
        if (vpa == null) return null;
        String accNo = vpaIndex.get(vpa.toLowerCase());
        return accNo != null ? accounts.get(accNo) : null;
    }

    public AccountRecord getByAccountNumber(String accountNumber) {
        return accounts.get(accountNumber);
    }

    /** Check if account has sufficient balance */
    public boolean hasSufficientBalance(String vpa, BigDecimal amount) {
        AccountRecord acc = getByVpa(vpa);
        if (acc == null) return false;
        return acc.balance.compareTo(amount) >= 0;
    }

    /** Debit account — thread-safe */
    public synchronized boolean debit(String vpa, BigDecimal amount, String txnId) {
        AccountRecord acc = getByVpa(vpa);
        if (acc == null) return false;
        if (acc.balance.compareTo(amount) < 0) return false;
        acc.balance = acc.balance.subtract(amount);
        System.out.println("[CBS-GATEWAY] Debited ₹" + amount + " from " + vpa
            + " | New balance: ₹" + acc.balance + " | txnId: " + txnId);
        return true;
    }

    /** Credit account — thread-safe */
    public synchronized boolean credit(String vpa, BigDecimal amount, String txnId) {
        AccountRecord acc = getByVpa(vpa);
        if (acc == null) return false;
        acc.balance = acc.balance.add(amount);
        System.out.println("[CBS-GATEWAY] Credited ₹" + amount + " to " + vpa
            + " | New balance: ₹" + acc.balance + " | txnId: " + txnId);
        return true;
    }

    /** Reverse debit — credit back to payer */
    public synchronized boolean reverseDebit(String vpa, BigDecimal amount, String txnId) {
        return credit(vpa, amount, txnId + "-REVERSAL");
    }

    /** Get balance for a VPA */
    public BigDecimal getBalance(String vpa) {
        AccountRecord acc = getByVpa(vpa);
        return acc != null ? acc.balance : BigDecimal.ZERO;
    }

    public Map<String, AccountRecord> getAllAccounts() {
        return accounts;
    }
}
