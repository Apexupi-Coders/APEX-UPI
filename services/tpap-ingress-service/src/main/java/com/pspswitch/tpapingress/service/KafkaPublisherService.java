package com.pspswitch.tpapingress.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.tpapingress.dto.request.BalanceInquiryRequest;
import com.pspswitch.tpapingress.dto.request.PaymentInitiateRequest;
import com.pspswitch.tpapingress.dto.request.VpaLookupRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes validated events to the correct Kafka topic.
 * Partition key = txnId (ensures ordering per transaction).
 * See architecture_spec.md Section 3.
 */
@Slf4j
@Service
public class KafkaPublisherService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.vpa-lookup}")
    private String vpaLookupTopic;

    @Value("${app.kafka.topics.balance-inquiry}")
    private String balanceInquiryTopic;

    @Value("${app.kafka.topics.payment-initiate}")
    private String paymentInitiateTopic;

    public KafkaPublisherService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean publishVpaLookup(VpaLookupRequest request) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("txnId", request.getTxnId());
        payload.put("correlationId", UUID.randomUUID().toString());
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("payerVpa", request.getRequesterVpa());
        payload.put("txnType", "VPA_LOOKUP");
        payload.put("requestTime", Instant.now().toString());

        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(vpaLookupTopic, request.getTxnId(), json);
            log.info("Published VPA_LOOKUP to topic {} for txnId={}", vpaLookupTopic, request.getTxnId());
        } catch (Exception e) {
            log.error("Failed to publish VPA_LOOKUP: {}", e.getMessage());
            return false;
        }
        return true;
    }

    public boolean publishBalanceInquiry(BalanceInquiryRequest request) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("txnId", request.getTxnId());
        payload.put("correlationId", UUID.randomUUID().toString());
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("payerVpa", request.getVpa());
        payload.put("currency", "INR");
        payload.put("txnType", "BALANCE");
        payload.put("requestTime", Instant.now().toString());

        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(balanceInquiryTopic, request.getTxnId(), json);
            log.info("Published BALANCE_INQUIRY to topic {} for txnId={}", balanceInquiryTopic, request.getTxnId());
        } catch (Exception e) {
            log.error("Failed to publish BALANCE_INQUIRY: {}", e.getMessage());
            return false;
        }
        return true;
    }

    public boolean publishPaymentInitiate(PaymentInitiateRequest request) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("txnId", request.getTxnId());
        payload.put("correlationId", UUID.randomUUID().toString());
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("payerVpa", request.getPayerVpa());
        payload.put("payeeVpa", request.getPayeeVpa());
        payload.put("payeeName", "Payee Name");
        payload.put("mcc", request.getMcc() != null ? request.getMcc() : "0000");
        
        if (request.getAmount() != null) {
            payload.put("amount", new java.math.BigDecimal(request.getAmount()));
        } else {
            payload.put("amount", java.math.BigDecimal.ZERO);
        }
        
        payload.put("currency", request.getCurrency() != null ? request.getCurrency() : "INR");
        payload.put("txnType", "PAY");
        payload.put("requestTime", Instant.now().toString());

        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(paymentInitiateTopic, request.getTxnId(), json);
            log.info("Published PAYMENT_INITIATE to topic {} for txnId={}", paymentInitiateTopic, request.getTxnId());
        } catch (Exception e) {
            log.error("Failed to publish PAYMENT_INITIATE: {}", e.getMessage());
            return false;
        }
        return true;
    }

    private String extractTpapId(String txnId) {
        if (txnId != null && txnId.contains("-")) {
            return txnId.substring(0, txnId.indexOf('-'));
        }
        return "unknown";
    }
}
