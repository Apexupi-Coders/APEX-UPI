package com.pspswitch.tpapegress.model.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for VPA_VERIFICATION events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VpaVerificationEvent {

    private String  vpa;
    
    @JsonAlias({"name", "accountHolderName"})
    private String  accountHolderName;
    
    private String  bankName;
    private boolean verified;
    private String  failureReason;
    
    @JsonSetter("status")
    public void setStatus(String status) {
        this.verified = "SUCCESS".equalsIgnoreCase(status);
    }
}

