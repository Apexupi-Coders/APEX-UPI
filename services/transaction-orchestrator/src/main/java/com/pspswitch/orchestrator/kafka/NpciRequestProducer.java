package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.NpciOutboundRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class NpciRequestProducer {
    private static final Logger log = LoggerFactory.getLogger(NpciRequestProducer.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public NpciRequestProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(NpciOutboundRequestEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("[KAFKA_PRODUCER] Sending NPCI request to topic npci.outbound.request | txnId={} | type={}", event.getTxnId(), event.getType());
            kafkaTemplate.send("npci.outbound.request", event.getTxnId(), payload);
        } catch (Exception e) {
            log.error("[KAFKA_PRODUCER] Failed to serialize NPCI request", e);
        }
    }
}
