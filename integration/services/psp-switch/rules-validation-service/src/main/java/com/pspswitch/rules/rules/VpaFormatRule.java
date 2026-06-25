package com.pspswitch.rules.rules;

import com.pspswitch.rules.model.ValidationRequest;
import com.pspswitch.rules.model.ValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@Order(1)
public class VpaFormatRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(VpaFormatRule.class);
    private static final Pattern VPA_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+@[a-zA-Z0-9]+$");

    @Override
    public ValidationResponse evaluate(ValidationRequest request) {
        log.info("[RULE] VpaFormat | txnId={}", request.getTxnId());
        if (!VPA_PATTERN.matcher(request.getPayerVpa()).matches()) {
            log.warn("[RULE] VpaFormat DENY | payerVpa={} invalid", request.getPayerVpa());
            return ValidationResponse.deny(request.getTxnId(), "INVALID_VPA_FORMAT", "Payer VPA format is invalid");
        }
        if (!VPA_PATTERN.matcher(request.getPayeeVpa()).matches()) {
            log.warn("[RULE] VpaFormat DENY | payeeVpa={} invalid", request.getPayeeVpa());
            return ValidationResponse.deny(request.getTxnId(), "INVALID_VPA_FORMAT", "Payee VPA format is invalid");
        }
        return null;
    }
}
