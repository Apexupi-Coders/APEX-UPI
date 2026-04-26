package com.pspswitch.rules.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
public class ValidationRequest {
    private String txnId;
    private String payerVpa;
    private String payeeVpa;
    private String payerAccountNumber;
    private BigDecimal amount;
    private String currency;
    private Instant timestamp;
}
