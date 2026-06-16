package com.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditConsumer {

    @Autowired
    private AuditService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = {
            "upi.transactions.initiated",
            "upi.npci.verified",
            "upi.cbs.debit.confirm",
            "upi.cbs.credit.confirm",
            "upi.cbs.reversal"
    })
    public void consume(String message) {

        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);

            String txnId = (String) data.get("txnId");
            String source = (String) data.get("source");
            String status = (String) data.get("status");
            String payer = (String) data.get("payer");
            String payee = (String) data.get("payee");
            Double amount = Double.valueOf(data.get("amount").toString());
            String stage = (String) data.get("stage");
            String remarks = (String) data.get("remarks");

            service.log(txnId, source, status, payer, payee, amount, stage, remarks);

            System.out.println("AUDIT STORED: " + txnId + " " + status);

        } catch (Exception e) {
            System.out.println("Kafka parse error: " + e.getMessage());
        }
    }
}