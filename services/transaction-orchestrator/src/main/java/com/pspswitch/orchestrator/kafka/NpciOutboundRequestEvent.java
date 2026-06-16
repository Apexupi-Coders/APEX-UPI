package com.pspswitch.orchestrator.kafka;

import java.math.BigDecimal;

/**
 * Kafka event published by Transaction Orchestrator to NPCI Adapter.
 */
public class NpciOutboundRequestEvent {

    private String txnId;
    private String correlationId;
    private String msgId;


    private String payerVpa;
    private String payeeVpa;
    private BigDecimal amount;
    private String type;

    private String timestamp;


    public NpciOutboundRequestEvent() {
    }

    public NpciOutboundRequestEvent(String txnId,
            String correlationId,
            String msgId,
            String payerVpa,
            String payeeVpa,
            BigDecimal amount,
            String type,
            String timestamp) {
        this.txnId = txnId;
        this.correlationId = correlationId;
        this.msgId = msgId;

        this.payerVpa = payerVpa;
        this.payeeVpa = payeeVpa;
        this.amount = amount;
        this.type = type;
        this.timestamp = timestamp;
    }


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

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }


    public String getPayerVpa() {
        return payerVpa;
    }

    public void setPayerVpa(String payerVpa) {
        this.payerVpa = payerVpa;
    }

    public String getPayeeVpa() {
        return payeeVpa;
    }

    public void setPayeeVpa(String payeeVpa) {
        this.payeeVpa = payeeVpa;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}

