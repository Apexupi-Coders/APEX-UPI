package com.hpe.psp_ledger_service;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PSPLedgerRepository
        extends JpaRepository<PSPLedger, Long> {
}