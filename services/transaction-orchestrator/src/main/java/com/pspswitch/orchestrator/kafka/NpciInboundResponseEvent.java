package com.pspswitch.orchestrator.kafka;

/**
 * Kafka event consumed by Transaction Orchestrator from NPCI Adapter.
 *
 * Adapter publishes fields:
 * - txnId, msgId, type
 * - result (SUCCESS/FAILURE/TIMEOUT/DEEMED)
 * - errCode
 *
 * Historically orchestrator expected fields like:
 * - correlationId, eventId, responseCode, status, approvalRefNo
 *
 * This DTO supports BOTH shapes so tests & integration can proceed.
 */
public class NpciInboundResponseEvent {

    private String txnId;

    // Adapter fields
    private String msgId;
    private String type;
    private String result;
    private String balance;
    private String currency;
    private String errCode;
    private String timestamp;

    // Legacy orchestrator fields
    private String correlationId;
    private String eventId;
    private String responseCode;
    private String approvalRefNo;
    private String status;

    public NpciInboundResponseEvent() {
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

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getApprovalRefNo() {
        return approvalRefNo;
    }

    public void setApprovalRefNo(String approvalRefNo) {
        this.approvalRefNo = approvalRefNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // Adapter getters/setters
    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getErrCode() {
        return errCode;
    }

    public void setErrCode(String errCode) {
        this.errCode = errCode;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}


