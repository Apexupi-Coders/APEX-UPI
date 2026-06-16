package com.pspswitch.tpapingress.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Transaction completion callback receiver.
 *
 * Endpoint used by Transaction Orchestrator NotificationService.
 *
 * POST /tpap/api/v1/tpap/callback
 */
@RestController
@RequestMapping("/tpap/api/v1/tpap")
@RequiredArgsConstructor
public class TransactionWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TransactionWebhookController.class);

    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> handleCallback(@RequestBody(required = false) TransactionWebhookPayload payload) {
        if (payload == null || payload.getTid() == null) {
            log.warn("[TPAP_CALLBACK] received invalid/empty payload");
            return ResponseEntity.ok(Map.of("status", "received"));
        }

        log.info("[TPAP_CALLBACK] received txnId={} state={} approvalRefNo={} correlationId={}",
                payload.getTid(), payload.getState(), payload.getApprovalRefNo(), payload.getCorrelationId());

        // For demo/integration environment: no persistence yet.
        // If you later want to update an existing transaction record,
        // wire this controller to the idempotency repository.

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    public static class TransactionWebhookPayload {
        private String tid;
        private String state;
        private String approvalRefNo;
        private BigDecimal amount;
        private String correlationId;
        private String pa; // optional, may be payee VPA

        public String getTid() {
            return tid;
        }

        public void setTid(String tid) {
            this.tid = tid;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getApprovalRefNo() {
            return approvalRefNo;
        }

        public void setApprovalRefNo(String approvalRefNo) {
            this.approvalRefNo = approvalRefNo;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        public String getPa() {
            return pa;
        }

        public void setPa(String pa) {
            this.pa = pa;
        }
    }
}

