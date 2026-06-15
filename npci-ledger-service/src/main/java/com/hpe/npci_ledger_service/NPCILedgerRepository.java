package com.hpe.npci_ledger_service;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NPCILedgerRepository
        extends JpaRepository<NPCILedger, Long> {
}