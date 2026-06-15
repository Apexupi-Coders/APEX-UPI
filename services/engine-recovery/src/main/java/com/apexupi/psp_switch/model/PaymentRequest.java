package com.apexupi.psp_switch.model;

public class PaymentRequest {
    public PaymentRequest() {
    }
    
    public PaymentRequest(String payer, String payee, double amount, String idempotencyKey) {
        this.payer = payer;
        this.payee = payee;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }
    
    public PaymentRequest(String payer, String payee, double amount) {
        this(payer, payee, amount, null);
    }
    
    private String payer;
    private String payee;
    private double amount;
    private String idempotencyKey;

    public String getPayer() {
        return payer;
    }

    public void setPayer(String payer) {
        this.payer = payer;
    }

    public String getPayee() {
        return payee;
    }

    public void setPayee(String payee) {
        this.payee = payee;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}

