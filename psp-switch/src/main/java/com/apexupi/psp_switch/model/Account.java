package com.apexupi.psp_switch.model;

public class Account {

    private String upiId;
    private double balance;

    public Account(String upiId, double balance) {
        this.upiId = upiId;
        this.balance = balance;
    }

    public String getUpiId() {
        return upiId;
    }

    public double getBalance() {
        return balance;
    }

    public void debit(double amount) {
        this.balance -= amount;
    }

    public void credit(double amount) {
        this.balance += amount;
    }
}