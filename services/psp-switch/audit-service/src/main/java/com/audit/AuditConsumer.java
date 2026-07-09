package com.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Audit Consumer — subscribes to all financial event topics and writes
 * an immutable audit log entry for every state change in the system.
 *
 * Fixes applied:
 * - Replaced System.out.println with SLF4J logger for structured logging.
 * - ObjectMapper injected via constructor (Spring-managed singleton) instead
 *   of creating a new instance per bean, which wastes memory and bypasses
 *   all global Jackson configuration.
 * - Null-safe amount parsing to prevent NullPointerException when the
 *   'amount' field is absent from non-financial event payloads.
 */
@Slf4j
@Component
public class AuditConsumer {

    private final AuditService service;
    private final ObjectMapper objectMapper;

    public AuditConsumer(AuditService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {
            "upi.transactions.initiated",
            "upi.npci.verified",
            "upi.cbs.debit.confirm",
            "upi.cbs.credit.confirm",
            "upi.cbs.reversal"
    }, groupId = "audit-service-consumer")
    public void consume(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);

            String txnId   = (String) data.get("txnId");
            String source  = (String) data.get("source");
            String status  = (String) data.get("status");
            String payer   = (String) data.get("payer");
            String payee   = (String) data.get("payee");
            String stage   = (String) data.get("stage");
            String remarks = (String) data.get("remarks");

            // Null-safe amount parsing: non-financial events may not carry an amount field
            Object rawAmount = data.get("amount");
            Double amount = (rawAmount != null) ? Double.valueOf(rawAmount.toString()) : 0.0;

            service.log(txnId, source, status, payer, payee, amount, stage, remarks);

            log.info("[AUDIT] Stored audit log | txnId={} | status={} | stage={}", txnId, status, stage);

        } catch (Exception e) {
            log.error("[AUDIT] Failed to process Kafka message | error={}", e.getMessage(), e);
        }
    }
}