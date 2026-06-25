package com.pspswitch.rules.rules;

import com.pspswitch.rules.config.RulesProperties;
import com.pspswitch.rules.model.ValidationRequest;
import com.pspswitch.rules.model.ValidationResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(6)
@RequiredArgsConstructor
public class DuplicateDetectionRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionRule.class);
    private final StringRedisTemplate redisTemplate;
    private final RulesProperties rulesProperties;

    @Override
    public ValidationResponse evaluate(ValidationRequest request) {
        log.info("[RULE] DuplicateDetection | txnId={}", request.getTxnId());
        String key = "duplicate:" + request.getPayerVpa() + ":" + request.getPayeeVpa() + ":" + request.getAmount();
        try {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(key, request.getTxnId(), Duration.ofSeconds(rulesProperties.getDedupWindowSeconds()));
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("[RULE] DuplicateDetection DENY | key={}", key);
                return ValidationResponse.deny(request.getTxnId(), "DUPLICATE_TRANSACTION",
                        "Duplicate transaction detected within the dedup window");
            }
        } catch (Exception e) {
            log.warn("[RULE] DuplicateDetection | Redis unavailable, skipping check | txnId={}", request.getTxnId(), e);
        }
        return null;
    }
}
