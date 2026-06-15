package com.hpe.psp_ledger_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PSPLedgerConsumer {

    @Autowired
    private PSPLedgerService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "upi.transactions.initiated")
    public void consume(String message) {

        try {

            Map<String,Object> data =
                    objectMapper.readValue(message, Map.class);

            String txnId =
                    (String) data.get("txnId");

            String payer =
                    (String) data.get("payer");

            String payee =
                    (String) data.get("payee");

            Double amount =
                    Double.valueOf(data.get("amount").toString());

            service.saveLedger(
                    txnId,
                    payer,
                    payee,
                    amount
            );

            System.out.println(
                    "PSP Ledger Updated : " + txnId
            );

        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}