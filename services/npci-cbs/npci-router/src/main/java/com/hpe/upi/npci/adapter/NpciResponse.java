package com.hpe.upi.npci.adapter;

import java.time.Instant;

/**
 * NpciResponse — represents an inbound response from the NPCI UPI network.
 *
 * In a real integration, NPCI sends back a response code (00 = success,
 * non-zero = failure) along with a response message. This POJO models that.
 *
 * Response codes follow NPCI UPI spec conventions:
 *  00  — Approved / Success
 *  01  — Refer to card issuer (insufficient funds)
 *  05  — Do not honour
 *  14  — Invalid VPA
 *  51  — Insufficient funds
 *  91  — Issuer or switch inoperative (timeout)
 *  96  — System malfunction
 */
public class NpciResponse {

    public enum ResponseStatus {
        SUCCESS, FAILURE, TIMEOUT, PENDING
    }

    private String msgId;               // echoed from NpciRequest
    private String txnId;               // UPI transaction ID
    private String rrn;
    private String responseCode;        // NPCI response code (00 = success)
    private String responseMessage;     // Human-readable response
    private ResponseStatus status;
    private NpciRequest.OperationType operation;
    private String npciTransactionId;   // NPCI's own transaction reference
    private Instant respondedAt;

    public NpciResponse() {
        this.respondedAt = Instant.now();
    }

    // Static factory methods for common responses
    public static NpciResponse success(String msgId, String txnId, String rrn,
                                        NpciRequest.OperationType op) {
        NpciResponse r = new NpciResponse();
        r.msgId = msgId;
        r.txnId = txnId;
        r.rrn = rrn;
        r.responseCode = "00";
        r.responseMessage = "Approved";
        r.status = ResponseStatus.SUCCESS;
        r.operation = op;
        r.npciTransactionId = "NPCI" + txnId;
        return r;
    }

    public static NpciResponse failure(String msgId, String txnId, String code, String message,
                                        NpciRequest.OperationType op) {
        NpciResponse r = new NpciResponse();
        r.msgId = msgId;
        r.txnId = txnId;
        r.responseCode = code;
        r.responseMessage = message;
        r.status = ResponseStatus.FAILURE;
        r.operation = op;
        return r;
    }

    public static NpciResponse timeout(String msgId, String txnId, NpciRequest.OperationType op) {
        NpciResponse r = new NpciResponse();
        r.msgId = msgId;
        r.txnId = txnId;
        r.responseCode = "91";
        r.responseMessage = "Issuer or switch inoperative";
        r.status = ResponseStatus.TIMEOUT;
        r.operation = op;
        return r;
    }

    // Getters and setters
    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }

    public ResponseStatus getStatus() { return status; }
    public void setStatus(ResponseStatus status) { this.status = status; }

    public NpciRequest.OperationType getOperation() { return operation; }
    public void setOperation(NpciRequest.OperationType operation) { this.operation = operation; }

    public String getNpciTransactionId() { return npciTransactionId; }
    public void setNpciTransactionId(String npciTransactionId) { this.npciTransactionId = npciTransactionId; }

    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }

    public boolean isSuccess() { return ResponseStatus.SUCCESS.equals(this.status); }
}
