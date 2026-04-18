package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import com.pspswitch.orchestrator.orchestrator.TransactionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer — consumes payment requests from the Ingress Service.
 *
 * Listens on the 'upi.txn.requests' topic. When the Ingress Service publishes
 * a validated payment request, this consumer picks it up and feeds it into
 * the TransactionOrchestrator — identical to the REST POST /api/v1/txn path.
 *
 * Architecture:
 * Ingress Service → [Kafka: upi.txn.requests] → PaymentRequestConsumer →
 * TransactionOrchestrator
 * REST POST /api/v1/txn ──────────────────────────────────────────────→
 * TransactionOrchestrator
 *
 * Both paths call the same orchestrator.orchestrate() method.
 */
@Component
public class PaymentRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestConsumer.class);

    private final TransactionOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public PaymentRequestConsumer(TransactionOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes a payment request from Kafka and triggers the orchestration saga.
     *
     * Message format: JSON-serialized UpiPaymentRequest
     * Consumer group: psp-orchestrator (configured in application.properties)
     */
    @KafkaListener(topics = "${app.kafka.topic.payment-requests}", groupId = "psp-orchestrator")
    public void consume(String message) {
        try {
            log.info("[KAFKA_CONSUMER] Received message from topic | length={}", message.length());

            UpiPaymentRequest request = objectMapper.readValue(message, UpiPaymentRequest.class);

            log.info("[KAFKA_CONSUMER] Deserialized | tr={} | pa={} | am={} | mode={}",
                    request.getTr(), request.getPa(), request.getAm(), request.getMode());

            // Feed into the same orchestrator pipeline as REST
            TransactionOrchestrator.OrchestratorResult result = orchestrator.orchestrate(request);

            if (result.isDuplicate()) {
                log.info("[KAFKA_CONSUMER] tr={} | DUPLICATE | Skipped (already processed)",
                        request.getTr());
            } else {
                log.info("[KAFKA_CONSUMER] tr={} | txnId={} | ACCEPTED | Saga initiated",
                        request.getTr(), result.getResponse().getTxnId());
            }

        } catch (Exception e) {
            log.error("[KAFKA_CONSUMER] Failed to process message: {}", e.getMessage(), e);
            // In production: send to dead-letter topic for manual review
        }
    }
}
