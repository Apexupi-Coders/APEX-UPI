package com.pspswitch.tpapegress.model.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for BALANCE_INQUIRY events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceInquiryEvent {

    @JsonAlias({"vpa", "payerVpa"})
    private String vpa;
    
    @JsonAlias({"balance", "availableBalance"})
    private String availableBalance;
    
    private String currency;
    
    @JsonAlias({"status", "inquiryStatus"})
    private String inquiryStatus;
}

