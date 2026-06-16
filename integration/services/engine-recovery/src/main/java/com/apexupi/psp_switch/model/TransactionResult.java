package com.apexupi.psp_switch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result from PSPProcessor.process()
 * Indicates stage reached and outcome.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResult {
    private String status; // SUCCESS, FAILED, COMPENSATED
    private String failureReason;
    private String stage; // NPCI, CBS_DEBIT, CBS_CREDIT
}

