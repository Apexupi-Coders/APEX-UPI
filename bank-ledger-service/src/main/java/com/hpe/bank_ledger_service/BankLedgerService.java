package com.hpe.bank_ledger_service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BankLedgerService {

    @Autowired
    private BankLedgerRepository repository;

    public void saveLedger(
            String txnId,
            String account,
            String entryType,
            Double amount) {

        BankLedger ledger = new BankLedger();

        ledger.setTxnId(txnId);
        ledger.setAccount(account);
        ledger.setEntryType(entryType);
        ledger.setAmount(amount);
        ledger.setStatus("SUCCESS");

        repository.save(ledger);

        System.out.println(
                "Bank Ledger Saved : " + txnId
        );
    }
}