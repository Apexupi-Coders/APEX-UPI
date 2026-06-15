package com.apexupi.psp_switch.service;

import com.apexupi.psp_switch.exception.IdempotencyMismatchException;
import com.apexupi.psp_switch.model.BalanceResponse;
import com.apexupi.psp_switch.model.PaymentRequest;
import com.apexupi.psp_switch.model.PaymentResponse;
import com.apexupi.psp_switch.cbs.CbsAccountStore;
import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.orchestrator.TransactionOrchestrator;
import com.apexupi.psp_switch.repository.JpaTransactionRepository;
import com.apexupi.psp_switch.repository.TransactionStore;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final TransactionStore store;
    private final CbsAccountStore cbsAccountStore;
    private final TransactionOrchestrator orchestrator;
    private final ValidationService validationService;
    private final CryptoService cryptoService;
    private final IdempotencyService idempotencyService;
    private final MonitoringService monitoringService;
    private final JpaTransactionRepository transactionRepo;
    
    public PaymentResponse processPayment(PaymentRequest request) {

        // Step 1 — Validate first (before touching Redis or DB)
        try {
            validationService.validatePaymentRequest(request);
        } catch (IllegalArgumentException e) {
            return new PaymentResponse(null, "ERROR", e.getMessage());
        }

        // Step 2 — Idempotency check
        String requestId = request.getIdempotencyKey();
        try {
            var resultOpt = idempotencyService.checkAndSet(request, requestId);
            if (resultOpt.isPresent()) {
                IdempotencyResult result = resultOpt.get();
                log.info("[PAYMENT] Duplicate request detected key={} txnId={}", requestId, result.txnId());
                return new PaymentResponse(result.txnId(), "DUPLICATE", "Duplicate request — idempotency key matched");
            }
        } catch (IdempotencyMismatchException e) {
            return new PaymentResponse(null, "MISMATCH", e.getMessage());
        }

        // Step 3 — Generate txnId
        String txnId = UUID.randomUUID().toString();

        // Step 4 — Save to JPA (store ORIGINAL payer/payee for CBS debit/credit)
        // We store the raw UPI IDs so CBSAdapter can look up the in-memory account.
        // CryptoService masking is used only for logs — not for storage in this demo.
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(txnId);
        txn.setPayer(request.getPayer());   // ← raw UPI ID (e.g. alice@hdfc)
        txn.setPayee(request.getPayee());   // ← raw UPI ID (e.g. bob@sbi)
        txn.setAmount(request.getAmount());
        txn.setIdempotencyKey(requestId);
        transactionRepo.save(txn);

        // Step 5 — Also save to TransactionStore (in-memory) for saga orchestration
        store.saveTransactionDetails(txnId, request);
        store.save(txnId, "PENDING");

        // Step 6 — Update idempotency record with txnId
        idempotencyService.updateStatus(requestId, txnId, "PENDING");
        // 🔹 Step 2 — Fraud evaluation (after validation, before orchestration)
// FraudDecision fraudDecision = fraudEngine.evaluate(request);
// monitoringService.addEvent(txnId, "FRAUD_SCORE_" + fraudDecision.getVerdict() + "_" + String.format("%.2f", fraudDecision.getScore()));

// if (fraudDecision.getVerdict() == FraudVerdict.BLOCK) {
//     log.warn("[PAYMENT] BLOCKED by fraud engine txnId={} reason={}", txnId, fraudDecision.getReason());
//     return new PaymentResponse(txnId, "REJECTED", "Blocked: " + fraudDecision.getReason());
// }

// if (fraudDecision.getVerdict() == FraudVerdict.REVIEW) {
//     log.warn("[PAYMENT] REVIEW flagged txnId={} score={}", txnId, fraudDecision.getScore());
//     monitoringService.addEvent(txnId, "FRAUD_REVIEW_FLAGGED");
//     // Continue processing but it's flagged in timeline
// }
        // Step 7 — Start orchestration (async webhook-driven flow)
        orchestrator.start(txnId);

        // Step 8 — Observability
        monitoringService.addEvent(txnId, "CREATED");

        log.info("[PAYMENT] txnId={} created payer={} payee={} amount={}",
                txnId, cryptoService.maskUpi(request.getPayer()), cryptoService.maskUpi(request.getPayee()), request.getAmount());

        return new PaymentResponse(txnId, "PENDING", "Payment accepted — processing via orchestrator");
    }

    public PaymentResponse getStatus(String txnId) {
        TransactionEntity txn = transactionRepo.findById(txnId).orElse(null);
        if (txn == null) {
            return new PaymentResponse(txnId, "NOT_FOUND", "Transaction not found");
        }
        return new PaymentResponse(txnId, txn.getState().name(), txn.getFailureReason());
    }

    public BalanceResponse getBalance(String upiId) {
        if (!cbsAccountStore.exists(upiId)) {
            return new BalanceResponse(upiId, 0.0);
        }
        return new BalanceResponse(upiId, cbsAccountStore.getBalance(upiId));
    }
}