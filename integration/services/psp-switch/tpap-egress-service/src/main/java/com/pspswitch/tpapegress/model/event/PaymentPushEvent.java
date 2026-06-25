package com.pspswitch.tpapegress.model.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for PAYMENT_PUSH events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPushEvent {

    private String payerVpa;
    private String payeeVpa;
    private String amount;
    private String currency;
    
    @JsonAlias({"approvalRefNo", "npciRrn"})
    private String npciRrn;
    
    @JsonAlias({"status", "txnStatus"})
    private String txnStatus;
    
    private String failureReason;
}

