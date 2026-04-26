package com.pspswitch.rules.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "transaction_summary")
public class TransactionSummary {

    @Id
    @GeneratedValue
    private UUID id;

    private String payerVpa;
    private String txnId;
    private BigDecimal amount;
    private String status;
    private LocalDate txnDate;
    private LocalDateTime createdAt = LocalDateTime.now();
}
