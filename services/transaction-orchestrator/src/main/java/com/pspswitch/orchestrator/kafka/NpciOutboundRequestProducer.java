package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Kafka Producer — publishes NPCI outbound request events.
 */
@Service
public class NpciOutboundRequestProducer {

    private static final Logger log = LoggerFactory.getLogger(NpciOutboundRequestProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.npci-outbound-request:npci.outbound.request}")
    private String outboundTopic;

    public NpciOutboundRequestProducer(KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(NpciOutboundRequestEvent event) {
        try {
            if (event.getCorrelationId() == null || event.getCorrelationId().isBlank()) {
                event.setCorrelationId(UUID.randomUUID().toString());
            }
            if (event.getMsgId() == null || event.getMsgId().isBlank()) {
                event.setMsgId(UUID.randomUUID().toString());
            }

            if (event.getTimestamp() == null || event.getTimestamp().isBlank()) {
                event.setTimestamp(java.time.Instant.now().toString());
            }


            String json = objectMapper.writeValueAsString(event);

            var msg = MessageBuilder.withPayload(json)
                    .setHeader(KafkaHeaders.TOPIC, outboundTopic)
                    .setHeader(KafkaHeaders.KEY, event.getTxnId())
                    .setHeader("correlationId", event.getCorrelationId())
                    .setHeader("msgId", event.getMsgId())

                    .build();

            kafkaTemplate.send(msg);

            log.info("[ORCH_OUTBOUND] txnId={} | topic={} | correlationId={} | msgId={} | status=published",
                    event.getTxnId(), outboundTopic, event.getCorrelationId(), event.getMsgId());


        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize NpciOutboundRequestEvent", e);
        }
    }
}

