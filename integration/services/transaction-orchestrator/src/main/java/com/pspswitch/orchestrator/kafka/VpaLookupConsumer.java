package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VpaLookupConsumer {
    private static final Logger log = LoggerFactory.getLogger(VpaLookupConsumer.class);

    private final ObjectMapper objectMapper;

    public VpaLookupConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "psp.vpa.lookup.request", groupId = "psp-orchestrator")
    public void consume(String message) {
        try {
            log.info("[KAFKA_CONSUMER] Received message from psp.vpa.lookup.request");
            JsonNode rootNode = objectMapper.readTree(message);
            
            String vpa = rootNode.has("payerVpa") ? rootNode.get("payerVpa").asText() : "unknown";
            log.info("[KAFKA_CONSUMER] VPA Lookup requested for: {}", vpa);
            
            // TODO: Wire up to npci.outbound.request type=VPA_LOOKUP in Integration-02

        } catch (Exception e) {
            log.error("[KAFKA_CONSUMER] Failed to process VPA lookup", e);
        }
    }
}
