package com.apexupi.psp_switch.reconciliation;

public enum RecoveryAction {
    RETRY_NPCI,         // re-submit to NPCI
    RETRY_CBS_DEBIT,    // re-trigger CBS debit
    COMPENSATE,         // reverse debit — credit never arrived
    MARK_UNKNOWN,       // timeout with no clear state
    MARK_MANUAL_REVIEW, // exhausted all recovery options
    NO_OP               // nothing to do
}

