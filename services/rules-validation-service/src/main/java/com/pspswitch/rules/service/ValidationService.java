package com.pspswitch.rules.service;

import com.pspswitch.rules.entity.ValidationLog;
import com.pspswitch.rules.model.ValidationRequest;
import com.pspswitch.rules.model.ValidationResponse;
import com.pspswitch.rules.repository.ValidationLogRepository;
import com.pspswitch.rules.rules.Rule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final List<Rule> rules;
    private final ValidationLogRepository validationLogRepository;

    public ValidationResponse validate(ValidationRequest request) {
        log.info("[VALIDATION] START | txnId={} | payerVpa={} | payeeVpa={} | amount={}",
                request.getTxnId(), request.getPayerVpa(), request.getPayeeVpa(), request.getAmount());

        for (Rule rule : rules) {
            ValidationResponse result = rule.evaluate(request);
            if (result != null) {
                // Rule returned a DENY — short-circuit
                log.warn("[VALIDATION] DENY | txnId={} | reason={}", request.getTxnId(), result.getReasonCode());
                auditAsync(request, result);
                return result;
            }
        }

        ValidationResponse allow = ValidationResponse.allow(request.getTxnId());
        log.info("[VALIDATION] ALLOW | txnId={}", request.getTxnId());
        auditAsync(request, allow);
        return allow;
    }

    @Async
    public void auditAsync(ValidationRequest request, ValidationResponse response) {
        try {
            ValidationLog log = new ValidationLog();
            log.setTxnId(request.getTxnId());
            log.setPayerVpa(request.getPayerVpa());
            log.setPayeeVpa(request.getPayeeVpa());
            log.setAmount(request.getAmount());
            log.setDecision(response.getDecision().name());
            log.setReasonCode(response.getReasonCode());
            validationLogRepository.save(log);
        } catch (Exception e) {
            ValidationService.log.error("[AUDIT] Failed to write validation_log | txnId={}", request.getTxnId(), e);
        }
    }
}
