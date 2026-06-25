package com.apexupi.psp_switch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context for PSP processing - extracted from PaymentRequest + txnId.
 * Passed to PSPProcessor.process()
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionContext {
    private String txnId;
    private String payer;
    private String payee;
    private double amount;
}
