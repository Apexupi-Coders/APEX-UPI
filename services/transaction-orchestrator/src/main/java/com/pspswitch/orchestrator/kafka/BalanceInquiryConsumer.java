package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.NpciOutboundRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class BalanceInquiryConsumer {
    private static final Logger log = LoggerFactory.getLogger(BalanceInquiryConsumer.class);

    private final ObjectMapper objectMapper;
    private final NpciRequestProducer npciRequestProducer;

    public BalanceInquiryConsumer(ObjectMapper objectMapper, NpciRequestProducer npciRequestProducer) {
        this.objectMapper = objectMapper;
        this.npciRequestProducer = npciRequestProducer;
    }

    @KafkaListener(topics = "psp.balance.inquiry.request", groupId = "psp-orchestrator")
    public void consume(String message) {
        try {
            log.info("[KAFKA_CONSUMER] Received message from psp.balance.inquiry.request");
            JsonNode rootNode = objectMapper.readTree(message);
            
            String txnId = rootNode.has("txnId") ? rootNode.get("txnId").asText() : "BAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String payerVpa = rootNode.has("payerVpa") ? rootNode.get("payerVpa").asText() : "unknown";

            NpciOutboundRequestEvent event = new NpciOutboundRequestEvent();
            event.setTxnId(txnId);
            event.setMsgId(UUID.randomUUID().toString());
            event.setType("BALANCE");
            event.setPayerVpa(payerVpa);
            event.setTimestamp(Instant.now().toString());

            npciRequestProducer.send(event);
            log.info("[KAFKA_CONSUMER] Dispatched BALANCE request to NPCI adapter | txnId={}", txnId);

        } catch (Exception e) {
            log.error("[KAFKA_CONSUMER] Failed to process balance inquiry", e);
        }
    }
}
