package com.apexupi.psp_switch.model;

public class PaymentResponse {

    private String txnId;
    private String status;
    private String message;

    public PaymentResponse(String txnId, String status, String message) {
        this.txnId = txnId;
        this.status = status;
        this.message = message;
    }

    public String getTxnId() {
        return txnId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}