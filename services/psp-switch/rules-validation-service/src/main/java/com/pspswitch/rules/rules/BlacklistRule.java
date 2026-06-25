package com.pspswitch.rules.rules;

import com.pspswitch.rules.model.ValidationRequest;
import com.pspswitch.rules.model.ValidationResponse;
import com.pspswitch.rules.repository.BlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@RequiredArgsConstructor
public class BlacklistRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(BlacklistRule.class);
    private final BlacklistRepository blacklistRepository;

    @Override
    public ValidationResponse evaluate(ValidationRequest request) {
        log.info("[RULE] Blacklist | txnId={}", request.getTxnId());
        if (blacklistRepository.existsByIdentifier(request.getPayerVpa())) {
            log.warn("[RULE] Blacklist DENY | payerVpa={} is blacklisted", request.getPayerVpa());
            return ValidationResponse.deny(request.getTxnId(), "PAYER_BLACKLISTED", "Payer VPA is blacklisted");
        }
        if (blacklistRepository.existsByIdentifier(request.getPayerAccountNumber())) {
            log.warn("[RULE] Blacklist DENY | payerAccount={} is blacklisted", request.getPayerAccountNumber());
            return ValidationResponse.deny(request.getTxnId(), "PAYER_BLACKLISTED", "Payer account is blacklisted");
        }
        if (blacklistRepository.existsByIdentifier(request.getPayeeVpa())) {
            log.warn("[RULE] Blacklist DENY | payeeVpa={} is blacklisted", request.getPayeeVpa());
            return ValidationResponse.deny(request.getTxnId(), "PAYEE_BLACKLISTED", "Payee VPA is blacklisted");
        }
        return null;
    }
}
