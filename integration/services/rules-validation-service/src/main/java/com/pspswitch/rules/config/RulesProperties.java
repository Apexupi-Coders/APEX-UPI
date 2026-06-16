package com.pspswitch.rules.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "rules")
public class RulesProperties {
    private BigDecimal maxTransactionAmount;
    private BigDecimal dailyLimit;
    private int velocityLimit;
    private int dedupWindowSeconds;
}
