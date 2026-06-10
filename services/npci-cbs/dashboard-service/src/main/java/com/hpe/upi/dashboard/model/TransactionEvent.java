package com.hpe.upi.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionEvent {
    private String txnId, rrn, payerVpa, payeeVpa, payerName, payeeName;
    private String payerBank, payeeBank, payerAccountNumber, payeeAccountNumber;
    private String amount, status, message, timestamp, dbSource, reversalReason;
    private String npciMsgId, npciRoutingCode, npciErrorCode;
    private String payerBalanceAfter, payeeBalanceAfter, payerBalanceAfterReversal;
    private boolean reversalInitiated;

    public String getTxnId(){return txnId;} public void setTxnId(String v){txnId=v;}
    public String getRrn(){return rrn;} public void setRrn(String v){rrn=v;}
    public String getPayerVpa(){return payerVpa;} public void setPayerVpa(String v){payerVpa=v;}
    public String getPayeeVpa(){return payeeVpa;} public void setPayeeVpa(String v){payeeVpa=v;}
    public String getPayerName(){return payerName;} public void setPayerName(String v){payerName=v;}
    public String getPayeeName(){return payeeName;} public void setPayeeName(String v){payeeName=v;}
    public String getPayerBank(){return payerBank;} public void setPayerBank(String v){payerBank=v;}
    public String getPayeeBank(){return payeeBank;} public void setPayeeBank(String v){payeeBank=v;}
    public String getPayerAccountNumber(){return payerAccountNumber;} public void setPayerAccountNumber(String v){payerAccountNumber=v;}
    public String getPayeeAccountNumber(){return payeeAccountNumber;} public void setPayeeAccountNumber(String v){payeeAccountNumber=v;}
    public String getAmount(){return amount;} public void setAmount(String v){amount=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public String getTimestamp(){return timestamp;} public void setTimestamp(String v){timestamp=v;}
    public String getDbSource(){return dbSource;} public void setDbSource(String v){dbSource=v;}
    public String getReversalReason(){return reversalReason;} public void setReversalReason(String v){reversalReason=v;}
    public String getNpciMsgId(){return npciMsgId;} public void setNpciMsgId(String v){npciMsgId=v;}
    public String getNpciRoutingCode(){return npciRoutingCode;} public void setNpciRoutingCode(String v){npciRoutingCode=v;}
    public String getNpciErrorCode(){return npciErrorCode;} public void setNpciErrorCode(String v){npciErrorCode=v;}
    public String getPayerBalanceAfter(){return payerBalanceAfter;} public void setPayerBalanceAfter(String v){payerBalanceAfter=v;}
    public String getPayeeBalanceAfter(){return payeeBalanceAfter;} public void setPayeeBalanceAfter(String v){payeeBalanceAfter=v;}
    public String getPayerBalanceAfterReversal(){return payerBalanceAfterReversal;} public void setPayerBalanceAfterReversal(String v){payerBalanceAfterReversal=v;}
    public boolean isReversalInitiated(){return reversalInitiated;} public void setReversalInitiated(boolean v){reversalInitiated=v;}
}
