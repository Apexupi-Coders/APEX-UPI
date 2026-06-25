package com.apexupi.psp_switch.reconciliation.rules;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.reconciliation.ReconciliationRule;
import com.apexupi.psp_switch.reconciliation.RecoveryAction;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Rule: Transaction submitted to orchestrator but NPCI was never called.
 * Happens if the orchestrator crashed between save and npciAdapter.submitTransaction().
 * Action: MARK_UNKNOWN — safe; no money has moved yet.
 */
@Component
public class SubmittedTimeoutRule implements ReconciliationRule {

    private static final int TIMEOUT_SECONDS = 60;

    @Override
    public boolean matches(TransactionEntity txn) {
        return txn.getState() == TransactionState.SUBMITTED
                && txn.getUpdatedAt() != null
                && txn.getUpdatedAt().isBefore(LocalDateTime.now().minusSeconds(TIMEOUT_SECONDS));
    }

    @Override
    public RecoveryAction resolve(TransactionEntity txn) {
        return RecoveryAction.MARK_UNKNOWN;
    }
}

