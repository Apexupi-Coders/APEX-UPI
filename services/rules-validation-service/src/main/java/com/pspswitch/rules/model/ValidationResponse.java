package com.pspswitch.rules.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponse {
    private String txnId;
    private ValidationDecision decision;
    private String reasonCode;
    private String message;

    public static ValidationResponse allow(String txnId) {
        return new ValidationResponse(txnId, ValidationDecision.ALLOW, null, "Transaction is valid");
    }

    public static ValidationResponse deny(String txnId, String reasonCode, String message) {
        return new ValidationResponse(txnId, ValidationDecision.DENY, reasonCode, message);
    }
}
