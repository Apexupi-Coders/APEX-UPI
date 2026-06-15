package com.hpe.npci_ledger_service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NPCILedgerService {

    @Autowired
    private NPCILedgerRepository repository;

    public void saveLedger(
            String txnId,
            String sourceBank,
            String destinationBank,
            Double amount) {

        NPCILedger ledger = new NPCILedger();

        ledger.setTxnId(txnId);
        ledger.setSourceBank(sourceBank);
        ledger.setDestinationBank(destinationBank);
        ledger.setAmount(amount);
        ledger.setStatus("VERIFIED");

        repository.save(ledger);

        System.out.println(
                "NPCI Ledger Saved : " + txnId
        );
    }
}