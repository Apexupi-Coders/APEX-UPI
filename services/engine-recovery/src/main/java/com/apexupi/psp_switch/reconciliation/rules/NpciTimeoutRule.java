package com.apexupi.psp_switch.reconciliation.rules;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.reconciliation.ReconciliationRule;
import com.apexupi.psp_switch.reconciliation.RecoveryAction;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Rule: NPCI was called but callback never arrived within 30 seconds.
 * Real-world cause: NPCI network timeout or webhook delivery failure.
 * Action: Mark UNKNOWN — operator must check NPCI portal.
 */
@Component
public class NpciTimeoutRule implements ReconciliationRule {

    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public boolean matches(TransactionEntity txn) {
        return txn.getState() == TransactionState.NPCI_CALLED
                && txn.getUpdatedAt() != null
                && txn.getUpdatedAt().isBefore(LocalDateTime.now().minusSeconds(TIMEOUT_SECONDS));
    }

    @Override
    public RecoveryAction resolve(TransactionEntity txn) {
        return RecoveryAction.MARK_UNKNOWN;
    }
}

