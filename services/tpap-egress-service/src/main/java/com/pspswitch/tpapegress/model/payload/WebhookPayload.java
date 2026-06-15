package com.pspswitch.tpapegress.model.payload;

import java.time.Instant;

/**
 * Common webhook payload envelope.
 * All concrete payloads implement this interface so the HTTP client
 * can accept any payload type polymorphically.
 */
public interface WebhookPayload {

    String  getEventId();
    String  getEventType();
    String  getTpapId();
    String  getTxnId();
    String  getCorrelationId();
    Instant getDeliveredAt();
}
