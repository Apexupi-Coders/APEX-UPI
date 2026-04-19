package com.pspswitch.tpapegress.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kafka message envelope for completed PSP Switch operations.
 * Consumed from topic: psp.switch.completed.events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchCompletedEvent {

    private String    eventId;
    private EventType eventType;
    private String    tpapId;
    private String    txnId;
    private String    correlationId;
    private Instant   timestamp;
    private String    schemaVersion;

    /**
     * Polymorphic payload — deserialized to the concrete event type
     * (PaymentPushEvent, BalanceInquiryEvent, VpaVerificationEvent)
     * by the handler that matches {@link #eventType}.
     */
    private Object    payload;
}
