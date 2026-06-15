package com.apexupi.psp_switch.reconciliation.rules;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;
import com.apexupi.psp_switch.reconciliation.ReconciliationRule;
import com.apexupi.psp_switch.reconciliation.RecoveryAction;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Rule: CBS debit succeeded (money left payer) but credit webhook never came.
 * This is the most financially dangerous stuck state — payer debited, payee never credited.
 * Action: COMPENSATE — reverse the debit to restore payer's balance.
 */
@Component
public class CbsCreditMissingRule implements ReconciliationRule {

    private static final int TIMEOUT_SECONDS = 20;

    @Override
    public boolean matches(TransactionEntity txn) {
        return txn.getState() == TransactionState.CBS_DEBIT_SUCCESS
                && txn.getUpdatedAt() != null
                && txn.getUpdatedAt().isBefore(LocalDateTime.now().minusSeconds(TIMEOUT_SECONDS));
    }

    @Override
    public RecoveryAction resolve(TransactionEntity txn) {
        return RecoveryAction.COMPENSATE;
    }
}

