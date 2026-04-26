package com.pspswitch.rules.rules;

import com.pspswitch.rules.config.RulesProperties;
import com.pspswitch.rules.model.ValidationRequest;
import com.pspswitch.rules.model.ValidationResponse;
import com.pspswitch.rules.repository.TransactionSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@Order(4)
@RequiredArgsConstructor
public class DailyLimitRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(DailyLimitRule.class);
    private final TransactionSummaryRepository transactionSummaryRepository;
    private final RulesProperties rulesProperties;

    @Override
    public ValidationResponse evaluate(ValidationRequest request) {
        log.info("[RULE] DailyLimit | txnId={} | payerVpa={}", request.getTxnId(), request.getPayerVpa());
        BigDecimal spentToday = transactionSummaryRepository
                .sumSuccessfulAmountByPayerVpaAndDate(request.getPayerVpa(), LocalDate.now());
        BigDecimal projectedTotal = spentToday.add(request.getAmount());
        if (projectedTotal.compareTo(rulesProperties.getDailyLimit()) > 0) {
            log.warn("[RULE] DailyLimit DENY | payerVpa={} | spentToday={} | amount={} | limit={}",
                    request.getPayerVpa(), spentToday, request.getAmount(), rulesProperties.getDailyLimit());
            return ValidationResponse.deny(request.getTxnId(), "DAILY_LIMIT_EXCEEDED",
                    "Payer has exceeded daily transaction limit");
        }
        return null;
    }
}
