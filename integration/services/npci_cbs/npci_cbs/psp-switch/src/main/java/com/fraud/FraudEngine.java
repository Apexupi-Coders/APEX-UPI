package com.fraud;

import com.apexupi.psp_switch.model.PaymentRequest;
import org.springframework.stereotype.Service;

@Service
public class FraudEngine {

    public FraudDecision evaluate(PaymentRequest request) {
        // Simple rule-based fraud detection for demo
        double score = 0.0;
        String reason = null;

        if (request.getAmount() > 5000) {
            score += 0.3;
            reason = "High amount";
        }

        if (request.getPayer() == null || request.getPayee() == null) {
            score += 0.5;
            reason = "Missing payer/payee";
        }

        FraudVerdict verdict;
        if (score >= 0.5) {
            verdict = FraudVerdict.BLOCK;
        } else if (score >= 0.2) {
            verdict = FraudVerdict.REVIEW;
        } else {
            verdict = FraudVerdict.ALLOW;
        }

        return new FraudDecision(verdict, score, reason);
    }
}

