package com.pspswitch.rules.rules;

import com.pspswitch.rules.config.RulesProperties;
import com.pspswitch.rules.model.ValidationRequest;
import com.pspswitch.rules.model.ValidationResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(2)
@RequiredArgsConstructor
public class AmountRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(AmountRule.class);
    private final RulesProperties rulesProperties;

    @Override
    public ValidationResponse evaluate(ValidationRequest request) {
        log.info("[RULE] Amount | txnId={} | amount={}", request.getTxnId(), request.getAmount());
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResponse.deny(request.getTxnId(), "INVALID_AMOUNT", "Amount must be greater than zero");
        }
        if (amount.compareTo(rulesProperties.getMaxTransactionAmount()) > 0) {
            log.warn("[RULE] Amount DENY | amount={} exceeds max={}", amount,
                    rulesProperties.getMaxTransactionAmount());
            return ValidationResponse.deny(request.getTxnId(), "AMOUNT_LIMIT_EXCEEDED",
                    "Amount exceeds maximum transaction limit of " + rulesProperties.getMaxTransactionAmount());
        }
        return null;
    }
}
