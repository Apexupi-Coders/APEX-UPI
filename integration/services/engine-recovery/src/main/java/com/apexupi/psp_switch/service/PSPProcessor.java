package com.apexupi.psp_switch.service;

import com.apexupi.psp_switch.model.TransactionContext;
import com.apexupi.psp_switch.model.TransactionResult;

/**
 * Interface for PSP processing engine.
 * Pure execution logic, no state/retry.
 * Modular, injectable.
 */
public interface PSPProcessor {
    TransactionResult process(TransactionContext context);
}
