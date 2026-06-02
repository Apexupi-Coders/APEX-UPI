package com.apexupi.psp_switch.model;

public enum TransactionState {
    PENDING,
    SUBMITTED,
    NPCI_CALLED,
    NPCI_PENDING,
    NPCI_SUCCESS,
    CBS_DEBIT_PENDING,
    CBS_DEBIT_SUCCESS,
    CBS_CREDIT_PENDING,
    SUCCESS,
    FAILED,
    COMPENSATED,
    UNKNOWN,
    MANUAL_REVIEW    // ← ADD THIS (escalation terminal state)
}
