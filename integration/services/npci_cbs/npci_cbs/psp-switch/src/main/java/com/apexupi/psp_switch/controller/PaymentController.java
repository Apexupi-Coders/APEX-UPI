package com.apexupi.psp_switch.controller;

import com.apexupi.psp_switch.model.BalanceResponse;
import com.apexupi.psp_switch.model.DlqEntry;
import com.apexupi.psp_switch.model.PaymentRequest;
import com.apexupi.psp_switch.model.PaymentResponse;
import com.apexupi.psp_switch.service.DlqService;
import com.apexupi.psp_switch.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final DlqService dlqService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@RequestBody PaymentRequest request) {
        log.info("[CONTROLLER] POST /payments payer={} payee={} amount={}",
                request.getPayer(), request.getPayee(), request.getAmount());

        PaymentResponse response = paymentService.processPayment(request);

        return switch (response.getStatus()) {
            case "ERROR"     -> ResponseEntity.badRequest().body(response);
            case "MISMATCH"  -> ResponseEntity.status(422).body(response);
            case "DUPLICATE" -> ResponseEntity.status(409).body(response);
            case "REJECTED"  -> ResponseEntity.status(503).body(response);
            default          -> ResponseEntity.ok(response);
        };
    }

    @GetMapping("/{txnId}")
    public ResponseEntity<PaymentResponse> getStatus(@PathVariable String txnId) {
        PaymentResponse response = paymentService.getStatus(txnId);
        if ("NOT_FOUND".equals(response.getStatus())) {
            return ResponseEntity.status(404).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance/{upiId}")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String upiId) {
        return ResponseEntity.ok(paymentService.getBalance(upiId));
    }

    @GetMapping("/dlq")
    public ResponseEntity<List<DlqEntry>> getDlq() {
        return ResponseEntity.ok(dlqService.getAllFailedTransactions());
    }

    @PostMapping("/dlq/retry/{txnId}")
    public ResponseEntity<String> retryFromDlq(@PathVariable String txnId) {
        dlqService.retryFromDLQ(txnId);
        return ResponseEntity.ok("Retried txnId: " + txnId);
    }
}