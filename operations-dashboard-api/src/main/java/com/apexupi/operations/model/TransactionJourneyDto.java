package com.apexupi.operations.model;

import java.math.BigDecimal;
import java.util.List;

public class TransactionJourneyDto {
    private String txnId;
    private String correlationId;
    private String approvalRefNo;
    private BigDecimal amount;
    private String payer;
    private String payee;

    private List<StateChangeDto> stateChanges;

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getApprovalRefNo() {
        return approvalRefNo;
    }

    public void setApprovalRefNo(String approvalRefNo) {
        this.approvalRefNo = approvalRefNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

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

    public List<StateChangeDto> getStateChanges() {
        return stateChanges;
    }

    public void setStateChanges(List<StateChangeDto> stateChanges) {
        this.stateChanges = stateChanges;
    }
}

