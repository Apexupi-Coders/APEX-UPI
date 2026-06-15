package com.fraud;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FraudDecision {
    private FraudVerdict verdict;
    private double score;
    private String reason;
}

