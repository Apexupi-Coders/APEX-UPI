package com.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_id")
    private String txnId;

    private String source;
    private String status;

    @Column(name = "payer_vpa")
    private String payerVpa;

    @Column(name = "payee_vpa")
    private String payeeVpa;

    private Double amount;
    private String stage;
    private String remarks;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ✅ ADD THESE (you missed this)

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPayerVpa(String payerVpa) {
        this.payerVpa = payerVpa;
    }

    public void setPayeeVpa(String payeeVpa) {
        this.payeeVpa = payeeVpa;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public String getTxnId() {
        return txnId;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public String getPayerVpa() {
        return payerVpa;
    }

    public String getPayeeVpa() {
        return payeeVpa;
    }

    public Double getAmount() {
        return amount;
    }

    public String getStage() {
        return stage;
    }

    public String getRemarks() {
        return remarks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}