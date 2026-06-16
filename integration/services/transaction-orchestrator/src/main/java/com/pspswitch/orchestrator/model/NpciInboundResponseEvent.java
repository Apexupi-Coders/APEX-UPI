package com.pspswitch.orchestrator.model;

import java.math.BigDecimal;

public class NpciInboundResponseEvent {
    private String txnId;
    private String msgId;
    private String type; // "PAY", "BALANCE", "VPA_LOOKUP"
    private String result; // "SUCCESS", "FAILURE", "TIMEOUT"
    private BigDecimal balance;
    private String currency;
    private String errCode;
    private String timestamp;

    public NpciInboundResponseEvent() {}

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getErrCode() { return errCode; }
    public void setErrCode(String errCode) { this.errCode = errCode; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
