package com.hpe.psp_ledger_service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PSPLedgerService {

    @Autowired
    private PSPLedgerRepository repository;

    public void saveLedger(String txnId,
                           String payer,
                           String payee,
                           Double amount) {

        PSPLedger ledger = new PSPLedger();

        ledger.setTxnId(txnId);
        ledger.setPayerVpa(payer);
        ledger.setPayeeVpa(payee);
        ledger.setAmount(amount);
        ledger.setStatus("INITIATED");

        repository.save(ledger);

        System.out.println("PSP Ledger Saved : " + txnId);
    }
}