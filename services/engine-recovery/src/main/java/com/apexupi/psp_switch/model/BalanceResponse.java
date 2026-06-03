package com.apexupi.psp_switch.model;

public class BalanceResponse {

    private String upiId;
    private double balance;

    public BalanceResponse(String upiId, double balance) {
        this.upiId = upiId;
        this.balance = balance;
    }

    public String getUpiId() {
        return upiId;
    }

    public double getBalance() {
        return balance;
    }
}