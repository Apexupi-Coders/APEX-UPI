package com.hpe.bank_ledger_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BankLedgerConsumer {

    @Autowired
    private BankLedgerService service;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @KafkaListener(
            topics = {
                    "upi.cbs.debit.confirm",
                    "upi.cbs.credit.confirm"
            }
    )
    public void consume(String message) {

        try {

            Map<String,Object> data =
                    objectMapper.readValue(message, Map.class);

            String txnId =
                    (String) data.get("txnId");

            String account =
                    (String) data.get("account");

            String entryType =
                    (String) data.get("entryType");

            Double amount =
                    Double.valueOf(data.get("amount").toString());

            service.saveLedger(
                    txnId,
                    account,
                    entryType,
                    amount
            );

            System.out.println(
                    "Bank Ledger Updated : " + txnId
            );

        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}