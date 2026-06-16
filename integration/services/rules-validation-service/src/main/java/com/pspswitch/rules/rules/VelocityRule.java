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
@Order(5)
@RequiredArgsConstructor
public class VelocityRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(VelocityRule.class);
    private final StringRedisTemplate redisTemplate;
    private final RulesProperties rulesProperties;

    @Override
    public ValidationResponse evaluate(ValidationRequest request) {
        log.info("[RULE] Velocity | txnId={} | payerVpa={}", request.getTxnId(), request.getPayerVpa());
        String key = "velocity:" + request.getPayerVpa();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(60));
            }
            if (count != null && count > rulesProperties.getVelocityLimit()) {
                log.warn("[RULE] Velocity DENY | payerVpa={} | count={} | limit={}",
                        request.getPayerVpa(), count, rulesProperties.getVelocityLimit());
                return ValidationResponse.deny(request.getTxnId(), "VELOCITY_LIMIT_EXCEEDED",
                        "Too many transactions in a short period");
            }
        } catch (Exception e) {
            log.warn("[RULE] Velocity | Redis unavailable, skipping check | txnId={}", request.getTxnId(), e);
        }
        return null;
    }
}
