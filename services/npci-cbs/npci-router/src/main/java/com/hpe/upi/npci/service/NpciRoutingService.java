package com.hpe.upi.npci.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hpe.upi.npci.adapter.NpciAdapterImpl;
import com.hpe.upi.npci.adapter.NpciRequest;
import com.hpe.upi.npci.adapter.ValidationResult;
import com.hpe.upi.npci.registry.VpaRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class NpciRoutingService {

    private static final long TIMEOUT_MS = 15_000;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final NpciAdapterImpl adapter;
    private final VpaRegistry vpaRegistry;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private final Map<String, Map<String, Object>> inFlight = new ConcurrentHashMap<>();
    private final Deque<Map<String, Object>> processedHistory = new ConcurrentLinkedDeque<>();

    public NpciRoutingService(KafkaTemplate<String, String> kafkaTemplate,
                               NpciAdapterImpl adapter,
                               VpaRegistry vpaRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.adapter = adapter;
        this.vpaRegistry = vpaRegistry;
    }

    public void handleIncomingTransaction(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        boolean simulateFailure = Boolean.TRUE.equals(txn.get("simulateFailure"));

        // Step 1: Validate via adapter (VPA registry + format + limits + duplicate)
        ValidationResult validation = adapter.validate(txn);
        if (!validation.isValid()) {
            System.err.println("[NPCI] Validation FAILED for " + txnId
                + " | code=" + validation.getErrorCode()
                + " | reason=" + validation.getErrorMessage());
            txn.put("status", "VALIDATION_FAILED");
            txn.put("failureReason", validation.getErrorCode() + ": " + validation.getErrorMessage());
            txn.put("npciErrorCode", validation.getErrorCode());
            publishDashboardEvent(txn, "NPCI validation failed [" + validation.getErrorCode() + "]: " + validation.getErrorMessage());
            publishToKafka("upi.transactions.status", txnId, txn);
            return;
        }

        // Enrich with VPA registry info
        enrichWithVpaInfo(txn);

        System.out.println("[NPCI] Received and validated transaction: " + txnId
            + " | simulateFailure=" + simulateFailure);

        txn.put("status", "ROUTING");
        txn.put("npciReceivedAt", Instant.now().toString());
        txn.put("simulateFailure", simulateFailure);
        inFlight.put(txnId, txn);
        publishDashboardEvent(txn, "NPCI received and routing transaction → " +
            txn.getOrDefault("payerName", "") + " → " + txn.getOrDefault("payeeName", ""));

        if (simulateFailure) {
            NpciRequest debitReq = adapter.toNpciRequest(txn, NpciRequest.OperationType.DEBIT);
            txn.put("npciMsgId", debitReq.getMsgId());
            txn.put("npciRoutingCode", debitReq.getNpciRoutingCode());
            txn.put("status", "DEBIT_SUCCESS");
            publishToKafka("upi.cbs.debit", txnId, txn);
        } else {
            NpciRequest debitReq = adapter.toNpciRequest(txn, NpciRequest.OperationType.DEBIT);
            txn.put("npciMsgId", debitReq.getMsgId());
            txn.put("npciRoutingCode", debitReq.getNpciRoutingCode());
            txn.put("payerAccountNumber", debitReq.getPayerAccountNumber());
            txn.put("payeeAccountNumber", debitReq.getPayeeAccountNumber());
            txn.put("status", "DEBIT_PENDING");
            publishToKafka("upi.cbs.debit", txnId, txn);
        }
    }

    public void handleDebitConfirmation(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        boolean simulateFailure = Boolean.TRUE.equals(txn.get("simulateFailure"));
        System.out.println("[NPCI] Debit confirmed for: " + txnId);

        if (simulateFailure) {
            txn.put("status", "CREDIT_PENDING");
            txn.put("creditBankStatus", "TIMEOUT_SIMULATED");
            inFlight.put(txnId, txn);
            publishDashboardEvent(txn, "Credit bank timeout — NPCI monitoring for reversal");
        } else {
            NpciRequest creditReq = adapter.toNpciRequest(txn, NpciRequest.OperationType.CREDIT);
            txn.put("npciMsgId", creditReq.getMsgId());
            txn.put("npciRoutingCode", creditReq.getNpciRoutingCode());
            txn.put("status", "CREDIT_PENDING");
            publishToKafka("upi.cbs.credit", txnId, txn);
        }
    }

    public void handleCreditConfirmation(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        System.out.println("[NPCI] Credit confirmed — transaction SUCCESS: " + txnId);

        txn.put("status", "SUCCESS");
        txn.put("completedAt", Instant.now().toString());
        inFlight.remove(txnId);

        archiveTransaction(txn);
        publishDashboardEvent(txn, "Transaction SUCCESS ✓ | "
            + txn.getOrDefault("payerName", txn.get("payerVpa")) + " paid ₹"
            + txn.get("amount") + " to "
            + txn.getOrDefault("payeeName", txn.get("payeeVpa")));
        publishToKafka("upi.transactions.status", txnId, txn);
    }

    @Scheduled(fixedDelay = 5000)
    public void detectAndInitiateReversal() {
        Instant now = Instant.now();
        for (Map.Entry<String, Map<String, Object>> entry : inFlight.entrySet()) {
            String txnId = entry.getKey();
            Map<String, Object> txn = entry.getValue();
            String status = (String) txn.get("status");

            if (!"CREDIT_PENDING".equals(status)) continue;
            if (Boolean.TRUE.equals(txn.get("reversalInitiated"))) continue;

            String receivedAt = (String) txn.get("npciReceivedAt");
            if (receivedAt == null) continue;

            long elapsedMs = now.toEpochMilli() - Instant.parse(receivedAt).toEpochMilli();
            if (elapsedMs > TIMEOUT_MS) {
                String reason = "Credit bank timed out after " + elapsedMs + "ms — NPCI auto-reversal";
                txn.put("status", "REVERSAL_INITIATED");
                txn.put("reversalInitiated", true);
                txn.put("reversalReason", reason);
                txn.put("reversalInitiatedAt", now.toString());

                NpciRequest reversalReq = adapter.toNpciRequest(txn, NpciRequest.OperationType.REVERSAL);
                txn.put("npciMsgId", reversalReq.getMsgId());
                txn.put("npciRoutingCode", reversalReq.getNpciRoutingCode());

                inFlight.put(txnId, txn);
                publishDashboardEvent(txn, "NPCI detected failure — initiating auto-reversal");
                publishToKafka("upi.cbs.reversal", txnId, txn);
            }
        }
    }

    public void handleReversalConfirmation(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        txn.put("status", "REVERSED");
        txn.put("reversalCompletedAt", Instant.now().toString());
        inFlight.remove(txnId);
        archiveTransaction(txn);
        publishDashboardEvent(txn, "Auto-reversal complete — ₹" + txn.get("amount") + " returned to payer");
        publishToKafka("upi.transactions.status", txnId, txn);
    }

    private void enrichWithVpaInfo(Map<String, Object> txn) {
        String payerVpa = (String) txn.get("payerVpa");
        String payeeVpa = (String) txn.get("payeeVpa");
        VpaRegistry.VpaRecord payer = vpaRegistry.lookup(payerVpa);
        VpaRegistry.VpaRecord payee = vpaRegistry.lookup(payeeVpa);
        if (payer != null) {
            txn.put("payerName", payer.accountName);
            txn.put("payerAccountNumber", payer.accountNumber);
            txn.put("payerIfsc", payer.ifsc);
        }
        if (payee != null) {
            txn.put("payeeName", payee.accountName);
            txn.put("payeeAccountNumber", payee.accountNumber);
            txn.put("payeeIfsc", payee.ifsc);
        }
    }

    private void publishToKafka(String topic, String key, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(topic, key, mapper.writeValueAsString(payload));
        } catch (Exception e) {
            System.err.println("[NPCI] Kafka publish error on topic " + topic + ": " + e.getMessage());
        }
    }

    private void publishDashboardEvent(Map<String, Object> txn, String message) {
        try {
            ObjectNode event = mapper.createObjectNode();
            event.put("txnId",             (String) txn.get("txnId"));
            event.put("rrn",               txn.getOrDefault("rrn", "").toString());
            event.put("payerVpa",          txn.getOrDefault("payerVpa", "").toString());
            event.put("payeeVpa",          txn.getOrDefault("payeeVpa", "").toString());
            event.put("payerName",         txn.getOrDefault("payerName", "").toString());
            event.put("payeeName",         txn.getOrDefault("payeeName", "").toString());
            event.put("payerAccountNumber",txn.getOrDefault("payerAccountNumber", "").toString());
            event.put("payeeAccountNumber",txn.getOrDefault("payeeAccountNumber", "").toString());
            event.put("payerBank",         txn.getOrDefault("payerBank", "").toString());
            event.put("payeeBank",         txn.getOrDefault("payeeBank", "").toString());
            event.put("amount",            txn.getOrDefault("amount", "0").toString());
            event.put("status",            txn.getOrDefault("status", "UNKNOWN").toString());
            event.put("message",           message);
            event.put("timestamp",         Instant.now().toString());
            event.put("npciMsgId",         txn.getOrDefault("npciMsgId", "").toString());
            event.put("npciRoutingCode",   txn.getOrDefault("npciRoutingCode", "").toString());
            event.put("npciErrorCode",     txn.getOrDefault("npciErrorCode", "").toString());
            event.put("reversalInitiated", Boolean.TRUE.equals(txn.get("reversalInitiated")));
            if (txn.get("reversalReason") != null)
                event.put("reversalReason", txn.get("reversalReason").toString());
            kafkaTemplate.send("upi.dashboard.events", (String) txn.get("txnId"), event.toString());
        } catch (Exception e) {
            System.err.println("[NPCI] Dashboard event error: " + e.getMessage());
        }
    }

    private void archiveTransaction(Map<String, Object> txn) {
        processedHistory.addFirst(new HashMap<>(txn));
        if (processedHistory.size() > 100) processedHistory.pollLast();
    }

    public List<Map<String, Object>> getInFlight() { return new ArrayList<>(inFlight.values()); }
    public List<Map<String, Object>> getHistory()  { return new ArrayList<>(processedHistory); }
}
