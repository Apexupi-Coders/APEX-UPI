package com.pspswitch.rules.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "validation_log")
public class ValidationLog {

    @Id
    @GeneratedValue
    private UUID id;

    private String txnId;
    private String payerVpa;
    private String payeeVpa;
    private BigDecimal amount;
    private String decision;
    private String reasonCode;
    private LocalDateTime evaluatedAt = LocalDateTime.now();
}
