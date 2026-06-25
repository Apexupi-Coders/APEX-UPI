package com.apexupi.psp_switch.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.apexupi.psp_switch.model.Account;
import com.apexupi.psp_switch.model.LedgerEntry;
import com.apexupi.psp_switch.model.PaymentRequest;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TransactionStore {
    // Account storage
    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    // Ledger storage
    private final List<LedgerEntry> ledger = new ArrayList<>();

    // Transaction details
    private final ConcurrentHashMap<String, PaymentRequest> txnDetails = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> retryCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> failureReasons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> transactions = new ConcurrentHashMap<>();
    
    private final RedisIdempotencyService redisIdempotencyService;

    @Autowired
    public TransactionStore(RedisIdempotencyService redisIdempotencyService) {
        this.redisIdempotencyService = redisIdempotencyService;
    }

    @PostConstruct
    public void init() {

        // Create test accounts
        createAccount("alice@hdfc", 10000);
        createAccount("bob@sbi", 5000);

        System.out.println("✅ Accounts initialized:");
        System.out.println("Alice balance: 10000");
        System.out.println("Bob balance: 5000");
    }

    // Transaction state
    public void save(String txnId, String status) {
        transactions.put(txnId, status);
    }

    public String getStatus(String txnId) {
        return transactions.get(txnId);
    }

    public void updateStatus(String txnId, String status) {
        transactions.put(txnId, status);
    }

    // Idempotency logic
    public boolean exists(String key) {
        return redisIdempotencyService.exists(key);
    }

    public void saveKey(String key, String txnId) {
        redisIdempotencyService.save(key, txnId);
    }

    public String getTxnId(String key) {
        return redisIdempotencyService.getTxnId(key);
    }

    @Value("${app.dlq.max-retries:3}")
    private int maxRetries;

    public int getRetryCount(String txnId) {
        return retryCount.getOrDefault(txnId, 0);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void resetRetryCount(String txnId) {
        retryCount.remove(txnId);
    }

    public void setFailureReason(String txnId, String reason) {
        failureReasons.put(txnId, reason);
    }

    public String getFailureReason(String txnId) {
        return failureReasons.getOrDefault(txnId, "UNKNOWN");
    }

    public void incrementRetry(String txnId) {
        retryCount.put(txnId, getRetryCount(txnId) + 1);
    }

    public void createAccount(String upiId, double balance) {
        accounts.put(upiId, new Account(upiId, balance));
    }

    public Account getAccount(String upiId) {
        return accounts.get(upiId);
    }

    public void saveTransactionDetails(String txnId, PaymentRequest request) {
        txnDetails.put(txnId, request);
    }

    public PaymentRequest getTransactionDetails(String txnId) {
        return txnDetails.get(txnId);
    }

    /**
     * Saga step: Debit payer account (used by TransactionOrchestratorService)
     * In production: NPCI debit API call
     */
    public synchronized boolean debitPayer(String txnId, String upiId, double amount) {
        Account payer = accounts.get(upiId);
        if (payer == null) {
            setFailureReason(txnId, "DEBIT_ACCOUNT_NOT_FOUND");
            log.warn("Debit failed - account not found: txnId={}, upiId={}", txnId, upiId);
            return false;
        }
        if (payer.getBalance() < amount) {
            setFailureReason(txnId, "DEBIT_INSUFFICIENT_BALANCE");
            log.warn("Debit failed - insufficient balance: txnId={}, balance={}, amount={}", txnId, payer.getBalance(), amount);
            return false;
        }
        payer.debit(amount);
        ledger.add(new LedgerEntry(txnId, upiId, null, amount, "DEBIT_PENDING"));
        log.info("Debit success: txnId={}, upiId={}, amount={}", txnId, upiId, amount);
        return true;
    }

    /**
     * Saga step: Credit payee account (used by TransactionOrchestratorService)
     * In production: NPCI credit API call
     */
    public synchronized boolean creditPayee(String txnId, String upiId, double amount) {
        Account payee = accounts.get(upiId);
        if (payee == null) {
            setFailureReason(txnId, "CREDIT_ACCOUNT_NOT_FOUND");
            log.warn("Credit failed - account not found: txnId={}, upiId={}", txnId, upiId);
            return false;
        }
        payee.credit(amount);
        ledger.add(new LedgerEntry(txnId, null, upiId, amount, "CREDIT"));
        log.info("Credit success: txnId={}, upiId={}, amount={}", txnId, upiId, amount);
        return true;
    }

    /**
     * Saga compensation: Idempotent rollback debit (reverse credit to payer)
     */
    public synchronized boolean compensatePayerDebit(String txnId, String upiId, double amount) {
        Account payer = accounts.get(upiId);
        if (payer == null) {
            log.error("Compensation failed - payer not found: txnId={}, upiId={}", txnId, upiId);
            return false;
        }
        payer.credit(amount); // Reverse debit
        ledger.add(new LedgerEntry(txnId, upiId, null, amount, "DEBIT_COMPENSATED"));
        String reason = getFailureReason(txnId);
        setFailureReason(txnId, reason + "_DEBIT_COMPENSATED");
        log.info("Compensation success: txnId={}, upiId={}, amount={}", txnId, upiId, amount);
        return true;
    }

    public synchronized boolean transfer(String txnId, String from, String to, double amount) {

        Account payer = accounts.get(from);
        Account payee = accounts.get(to);

        // Check accounts exist
        if (payer == null || payee == null) {
            setFailureReason(txnId, "ACCOUNT_NOT_FOUND");
            log.warn("Transfer failed ACCOUNT_NOT_FOUND: txnId={}, from={}, to={}", txnId, from, to);
            return false;
        }

        // Check balance
        if (payer.getBalance() < amount) {
            setFailureReason(txnId, "INSUFFICIENT_BALANCE");
            log.warn("Transfer failed INSUFFICIENT_BALANCE: txnId={}, payerBalance={}, amount={}", txnId, payer.getBalance(), amount);
            return false;
        }

        // DEBIT payer
        payer.debit(amount);
        ledger.add(new LedgerEntry(txnId, from, to, amount, "DEBIT"));

        // CREDIT payee
        payee.credit(amount);
        ledger.add(new LedgerEntry(txnId, from, to, amount, "CREDIT"));

        log.info("Transfer success: txnId={}, from={}, to={}, amount={}", txnId, from, to, amount);
        return true;
    }
}
