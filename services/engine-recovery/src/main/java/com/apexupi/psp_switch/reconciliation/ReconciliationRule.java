package com.apexupi.psp_switch.reconciliation;

import com.apexupi.psp_switch.model.TransactionEntity;

/**
 * Strategy interface — each implementation detects one class of stuck transaction.
 * Add new rules by creating new @Component classes — scanner picks them up automatically.
 */
public interface ReconciliationRule {
    /** Return true if this rule applies to the given stuck transaction */
    boolean matches(TransactionEntity txn);

    /** Return the recovery action to take */
    RecoveryAction resolve(TransactionEntity txn);
}

