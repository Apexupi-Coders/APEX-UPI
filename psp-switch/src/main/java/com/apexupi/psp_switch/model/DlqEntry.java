package com.apexupi.psp_switch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqEntry {
    private String txnId;
    private String payer;
    private String payee;
    private double amount;
    private String failureReason;
    private int retryCount;
    private LocalDateTime timestamp;
    private String status;
}

