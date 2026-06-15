package com.hpe.bank_ledger_service;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankLedgerRepository
        extends JpaRepository<BankLedger, Long> {
}