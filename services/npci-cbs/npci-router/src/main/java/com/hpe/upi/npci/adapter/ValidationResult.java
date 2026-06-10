package com.hpe.upi.npci.adapter;

/**
 * ValidationResult — returned by NpciProtocolAdapter.validate().
 *
 * Encapsulates whether an incoming transaction passed NPCI validation rules:
 *  - VPA format check (must contain @)
 *  - Amount range check (must be > 0 and <= 1,00,000 per UPI limits)
 *  - Mandatory field presence (txnId, rrn, payerVpa, payeeVpa, amount)
 *  - Payer and payee VPA must not be the same
 */
public class ValidationResult {

    private final boolean valid;
    private final String errorCode;
    private final String errorMessage;

    private ValidationResult(boolean valid, String errorCode, String errorMessage) {
        this.valid = valid;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, null, null);
    }

    public static ValidationResult fail(String errorCode, String errorMessage) {
        return new ValidationResult(false, errorCode, errorMessage);
    }

    public boolean isValid() { return valid; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        return valid ? "ValidationResult{OK}" :
               "ValidationResult{FAIL code=" + errorCode + " msg=" + errorMessage + "}";
    }
}
