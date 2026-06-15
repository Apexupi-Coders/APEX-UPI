package com.pspswitch.rules.rules;

import com.pspswitch.rules.model.ValidationRequest;
import com.pspswitch.rules.model.ValidationResponse;

public interface Rule {
    /**
     * Evaluate the rule.
     * 
     * @return null if the rule passes, or a DENY ValidationResponse if it fails.
     */
    ValidationResponse evaluate(ValidationRequest request);
}
