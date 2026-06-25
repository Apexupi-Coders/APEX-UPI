package com.hpe.npci_ledger_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NPCILedgerConsumer {

    @Autowired
    private NPCILedgerService service;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @KafkaListener(
            topics = "upi.npci.verified"
    )
    public void consume(String message) {

        try {

            Map<String,Object> data =
                    objectMapper.readValue(
                            message,
                            Map.class
                    );

            String txnId =
                    (String) data.get("txnId");

            String sourceBank =
                    (String) data.get("sourceBank");

            String destinationBank =
                    (String) data.get("destinationBank");

            Double amount =
                    Double.valueOf(
                            data.get("amount").toString()
                    );

            service.saveLedger(
                    txnId,
                    sourceBank,
                    destinationBank,
                    amount
            );

            System.out.println(
                    "NPCI Ledger Updated : " + txnId
            );

        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}