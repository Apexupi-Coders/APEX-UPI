package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.NpciInboundResponseEvent;
import com.pspswitch.orchestrator.service.NpciCallbackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NpciResponseConsumer {
    private static final Logger log = LoggerFactory.getLogger(NpciResponseConsumer.class);

    private final ObjectMapper objectMapper;
    private final NpciCallbackHandler callbackHandler;

    public NpciResponseConsumer(ObjectMapper objectMapper, NpciCallbackHandler callbackHandler) {
        this.objectMapper = objectMapper;
        this.callbackHandler = callbackHandler;
    }

    @KafkaListener(topics = "npci.inbound.response", groupId = "psp-orchestrator")
    public void consume(String message) {
        try {
            log.info("[KAFKA_CONSUMER] Received message from npci.inbound.response");
            NpciInboundResponseEvent event = objectMapper.readValue(message, NpciInboundResponseEvent.class);
            log.info("[KAFKA_CONSUMER] Deserialized NPCI response | txnId={} | type={} | result={}", 
                     event.getTxnId(), event.getType(), event.getResult());

            if ("PAY".equalsIgnoreCase(event.getType())) {
                callbackHandler.handleNpciResponse(event);
            } else if ("BALANCE".equalsIgnoreCase(event.getType())) {
                log.info("[KAFKA_CONSUMER] Received BALANCE response | txnId={} | balance={}", event.getTxnId(), event.getBalance());
            } else {
                log.warn("[KAFKA_CONSUMER] Unknown response type: {}", event.getType());
            }

        } catch (Exception e) {
            log.error("[KAFKA_CONSUMER] Failed to process NPCI response", e);
        }
    }
}
