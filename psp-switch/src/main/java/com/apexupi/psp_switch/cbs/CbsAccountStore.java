package com.apexupi.psp_switch.cbs;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe in-memory account store for CBS simulation.
 *
 * Uses per-account locking so concurrent operations on different
 * accounts don't block each other — only same-account ops serialize.
 *
 * Production equivalent: Core Banking DB with row-level locking.
 */
@Component
@Slf4j
public class CbsAccountStore {

    // balance store: upiId → balance (in paise for precision, but kept as double for demo)
    private final ConcurrentHashMap<String, Double> balances = new ConcurrentHashMap<>();

    // per-account lock: upiId → ReentrantLock
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        register("alice@hdfc", 10000.0);
        register("bob@sbi",   5000.0);
        log.info("[CBS][ACCOUNT_STORE] Initialized: alice@hdfc=10000, bob@sbi=5000");
    }

    public void register(String upiId, double initialBalance) {
        balances.put(upiId, initialBalance);
        locks.put(upiId, new ReentrantLock());
        log.info("[CBS][ACCOUNT_STORE] Registered account upiId={} balance={}", upiId, initialBalance);
    }

    public boolean exists(String upiId) {
        return balances.containsKey(upiId);
    }

    public double getBalance(String upiId) {
        return balances.getOrDefault(upiId, -1.0);
    }

    /**
     * Atomically debit an account.
     * Returns false if account missing or insufficient balance.
     */
    public boolean debit(String upiId, double amount) {
        ReentrantLock lock = locks.get(upiId);
        if (lock == null) return false;

        lock.lock();
        try {
            double current = balances.getOrDefault(upiId, 0.0);
            if (current < amount) return false;
            balances.put(upiId, current - amount);
            log.info("[CBS][ACCOUNT_STORE] Debited upiId={} amount={} newBalance={}", upiId, amount, current - amount);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically credit an account.
     * Returns false only if account doesn't exist.
     */
    public boolean credit(String upiId, double amount) {
        ReentrantLock lock = locks.get(upiId);
        if (lock == null) return false;

        lock.lock();
        try {
            double current = balances.getOrDefault(upiId, 0.0);
            balances.put(upiId, current + amount);
            log.info("[CBS][ACCOUNT_STORE] Credited upiId={} amount={} newBalance={}", upiId, amount, current + amount);
            return true;
        } finally {
            lock.unlock();
        }
    }
}
