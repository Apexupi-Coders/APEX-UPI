package com.apexupi.psp_switch.service;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.apexupi.psp_switch.model.PaymentRequest;

import jakarta.validation.Valid;

@Service
@Validated
public class ValidationService {

    public void validatePaymentRequest(@Valid PaymentRequest request) {
        if (!isValidUpi(request.getPayer())) {
            throw new IllegalArgumentException("Invalid payer UPI ID");
        }
        if (!isValidUpi(request.getPayee())) {
            throw new IllegalArgumentException("Invalid payee UPI ID");
        }
        if (request.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getAmount() > 100000) {
            throw new IllegalArgumentException("Amount exceeds daily limit");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency key required");
        }
    }

    private boolean isValidUpi(String upiId) {
        return upiId != null && upiId.matches("^[a-zA-Z0-9.-]+@[a-zA-Z0-9.-]+$");
    }
}

