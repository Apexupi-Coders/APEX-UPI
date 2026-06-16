package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
<<<<<<< HEAD
=======
import com.pspswitch.orchestrator.exception.ValidationException;
>>>>>>> c24d976 (Initial commit)
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import com.pspswitch.orchestrator.orchestrator.TransactionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

<<<<<<< HEAD
=======



>>>>>>> c24d976 (Initial commit)
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
<<<<<<< HEAD
    @KafkaListener(topics = "${app.kafka.topic.payment-requests}", groupId = "psp-orchestrator")
    public void consume(String message) {
        try {
            log.info("[KAFKA_CONSUMER] Received message from topic | length={}", message.length());

            JsonNode rootNode = objectMapper.readTree(message);
            
            // Map flat TPAP JSON to Orchestrator UpiPaymentRequest
            UpiPaymentRequest request = new UpiPaymentRequest();
            request.setTr(rootNode.has("txnId") ? rootNode.get("txnId").asText() : java.util.UUID.randomUUID().toString());
            request.setPayerVpa(rootNode.has("payerVpa") ? rootNode.get("payerVpa").asText() : "unknown@upi");
            request.setPa(rootNode.has("payeeVpa") ? rootNode.get("payeeVpa").asText() : null);
            request.setPn(rootNode.has("payeeName") ? rootNode.get("payeeName").asText() : "Payee");
            request.setMc(rootNode.has("mcc") ? rootNode.get("mcc").asText() : "0000");
            if (rootNode.has("amount")) {
                request.setAm(new java.math.BigDecimal(rootNode.get("amount").asText()));
            }
            request.setCu(rootNode.has("currency") ? rootNode.get("currency").asText() : "INR");

            // Map txnType to UPI mode code:
            // PEER_TO_PEER → mode 04 (P2P, no merchant IDs needed)
            // MERCHANT_PAYMENT → mode 16 (QR merchant, requires mid/msid/mtid)
            String txnType = rootNode.has("txnType") ? rootNode.get("txnType").asText() : "PEER_TO_PEER";
            if ("MERCHANT_PAYMENT".equals(txnType)) {
                request.setMode("16");
                request.setMid(rootNode.has("mid") ? rootNode.get("mid").asText() : "MERCHANT-001");
                request.setMsid(rootNode.has("msid") ? rootNode.get("msid").asText() : "STORE-001");
                request.setMtid(rootNode.has("mtid") ? rootNode.get("mtid").asText() : "TERM-001");
            } else {
                request.setMode("04"); // P2P — no merchant IDs required
            }

            request.setFlowDirection("SEND");
            request.setSignatureVerified(true);

            log.info("[KAFKA_CONSUMER] Deserialized and Mapped | tr={} | payerVpa={} | pa={} | am={} | mode={} | txnType={}",
                    request.getTr(), request.getPayerVpa(), request.getPa(), request.getAm(), request.getMode(), txnType);

            // Feed into the same orchestrator pipeline as REST
            TransactionOrchestrator.OrchestratorResult result = orchestrator.orchestrate(request);

            if (result.isDuplicate()) {
                log.info("[KAFKA_CONSUMER] tr={} | DUPLICATE | Skipped (already processed)",
                        request.getTr());
=======
    @KafkaListener(topics = {
            "psp.payment.initiate.request",
            "psp.balance.inquiry.request"
    }, groupId = "psp-orchestrator")
    public void consume(String message) {

        try {
            log.info("[KAFKA_CONSUMER] Received message from topic | length={}", message.length());

            // TPAP payload uses external field names:
            // txnId, payerVpa, payeeVpa, amount, currency, txnType, mcc
            // while UpiPaymentRequest expects:
            // tr, pa, pn, am, cu, mode, mc
            // Using JsonNode first allows explicit, validated mapping.
            JsonNode root = objectMapper.readTree(message);

            // TPAP publishes an envelope: { ..., "data": { txnId, payerVpa, ... } }
            // Some producers may still send a flat payload. Support both.
            JsonNode dataNode = root.hasNonNull("data") ? root.get("data") : root;

            UpiPaymentRequest request = mapTpapPayloadToUpiPaymentRequest(dataNode);


            log.info("[KAFKA_CONSUMER] Mapped payload | tr={} pa={} pn={} am={} cu={} mode={} mc={} tid={}",
                    request.getTr(), request.getPa(), request.getPn(), request.getAm(), request.getCu(),
                    request.getMode(), request.getMc(), request.getTid());

            // Fail fast before orchestration to avoid NPE paths.
            validateMappedRequest(request);

            TransactionOrchestrator.OrchestratorResult result = orchestrator.orchestrate(request);

            if (result.isDuplicate()) {
                log.info("[KAFKA_CONSUMER] tr={} | DUPLICATE | Skipped (already processed)", request.getTr());
>>>>>>> c24d976 (Initial commit)
            } else {
                log.info("[KAFKA_CONSUMER] tr={} | txnId={} | ACCEPTED | Saga initiated",
                        request.getTr(), result.getResponse().getTxnId());
            }

<<<<<<< HEAD
=======
        } catch (ValidationException ve) {
            log.warn("[KAFKA_CONSUMER] Rejected malformed payload | reason={}", ve.getMessage());
>>>>>>> c24d976 (Initial commit)
        } catch (Exception e) {
            log.error("[KAFKA_CONSUMER] Failed to process message: {}", e.getMessage(), e);
            // In production: send to dead-letter topic for manual review
        }
    }
<<<<<<< HEAD
}
=======

    private UpiPaymentRequest mapTpapPayloadToUpiPaymentRequest(JsonNode root) {
        UpiPaymentRequest req = new UpiPaymentRequest();

        req.setTr(text(root, "txnId"));
        req.setPa(text(root, "payerVpa"));
        req.setPn(text(root, "payeeVpa"));

        if (root.hasNonNull("amount")) {
            // Prefer numeric if present; fallback to string parsing.
            try {
                JsonNode amountNode = root.get("amount");
                if (amountNode == null || amountNode.isNull()) {
                    req.setAm(null);
                } else if (amountNode.isNumber()) {
                    req.setAm(amountNode.decimalValue());
                } else {
                    String amountText = amountNode.asText();
                    req.setAm(amountText == null || amountText.isBlank() ? null : new java.math.BigDecimal(amountText));
                }
            } catch (Exception e) {
                // Leave null; validation will reject with a clear reason.
                req.setAm(null);
            }

        }

        req.setCu(text(root, "currency"));
        req.setMode(text(root, "txnType"));
        req.setMc(text(root, "mcc"));

        return req;
    }

    private void validateMappedRequest(UpiPaymentRequest request) {
        // Mirror the orchestrator's critical fields early.

        if (request.getTr() == null) {
            throw new ValidationException("Missing txnId (mapped to tr)");
        }
        if (request.getPa() == null) {
            throw new ValidationException("Missing payerVpa (mapped to pa)");
        }
        if (request.getPn() == null) {
            throw new ValidationException("Missing payeeVpa (mapped to pn)");
        }
        if (request.getAm() == null) {
            throw new ValidationException("Missing amount (mapped to am)");
        }
        if (request.getCu() == null) {
            throw new ValidationException("Missing currency (mapped to cu)");
        }
        if (request.getMode() == null) {
            throw new ValidationException("Missing txnType (mapped to mode)");
        }
        if (request.getMc() == null) {
            throw new ValidationException("Missing mcc (mapped to mc)");
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return null;
        String v = n.asText();
        return v == null || v.isBlank() ? null : v;
    }
}

>>>>>>> c24d976 (Initial commit)
