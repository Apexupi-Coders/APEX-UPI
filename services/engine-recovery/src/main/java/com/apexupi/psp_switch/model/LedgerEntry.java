package com.apexupi.psp_switch.model;

public class LedgerEntry {

    private String txnId;
    private String from;
    private String to;
    private double amount;
    private String type;

    public LedgerEntry(String txnId, String from, String to, double amount, String type) {
        this.txnId = txnId;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.type = type;
    }

    public String getTxnId() {
        return txnId;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public double getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }
}