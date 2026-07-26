package com.pspswitch.tpapingress.service;

import com.pspswitch.tpapingress.dto.request.BalanceInquiryRequest;
import com.pspswitch.tpapingress.dto.request.PaymentInitiateRequest;
import com.pspswitch.tpapingress.dto.request.VpaLookupRequest;
import com.pspswitch.tpapingress.exception.KafkaPublishFailureException;
import com.pspswitch.tpapingress.kafka.KafkaEventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes validated events to the correct Kafka topic.
 * Partition key = txnId (ensures ordering per transaction).
 * See architecture_spec.md Section 3.
 */
@Slf4j
@Service
public class KafkaPublisherService {

    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.vpa-lookup}")
    private String vpaLookupTopic;

    @Value("${app.kafka.topics.balance-inquiry}")
    private String balanceInquiryTopic;

    @Value("${app.kafka.topics.payment-initiate}")
    private String paymentInitiateTopic;

    public KafkaPublisherService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishVpaLookup(VpaLookupRequest request) {
        publish(vpaLookupTopic, "VPA_LOOKUP_REQUESTED", request.getTxnId(), request);
    }

    public void publishBalanceInquiry(BalanceInquiryRequest request) {
        publish(balanceInquiryTopic, "BALANCE_INQUIRY_REQUESTED", request.getTxnId(), request);
    }

    public void publishPaymentInitiate(PaymentInitiateRequest request) {
        publish(paymentInitiateTopic, "PAYMENT_INITIATE_REQUESTED", request.getTxnId(), request);
    }

    private void publish(String topic, String eventType, String txnId, Object data) {
        KafkaEventEnvelope envelope = KafkaEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .tpapId(extractTpapId(txnId))
                .txnId(txnId)
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toString())
                .schemaVersion("1.0")
                .data(data)
                .build();

        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, txnId, envelope);

            SendResult<String, Object> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("[KAFKA-PUBLISHER] Published {} to topic={} | txnId={} | partition={} | offset={}",
                    eventType, topic, txnId,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception ex) {
            log.error("[KAFKA-PUBLISHER] Publish FAILED | topic={} | txnId={} | error={}",
                    topic, txnId, ex.getMessage(), ex);
            throw new KafkaPublishFailureException(
                    "Failed to publish " + eventType + " for txnId=" + txnId);
        }
    }

    private String extractTpapId(String txnId) {
        if (txnId != null && txnId.contains("-")) {
            return txnId.substring(0, txnId.indexOf('-'));
        }
        return "unknown";
    }
}
