package com.pspswitch.orchestrator.model;

import java.math.BigDecimal;

public class NpciOutboundRequestEvent {
    private String txnId;
    private String msgId;
    private String type; // "PAY", "BALANCE", "VPA_LOOKUP"
    private String payerVpa;
    private String payeeVpa;
    private BigDecimal amount;
    private String currency;
    private String timestamp;

    public NpciOutboundRequestEvent() {}

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPayerVpa() { return payerVpa; }
    public void setPayerVpa(String payerVpa) { this.payerVpa = payerVpa; }

    public String getPayeeVpa() { return payeeVpa; }
    public void setPayeeVpa(String payeeVpa) { this.payeeVpa = payeeVpa; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
