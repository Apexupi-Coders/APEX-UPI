package com.pspswitch.tpapegress.exception;

/**
 * Thrown by WebhookHttpClient on network-level failures:
 * connection refused, read timeout, DNS resolution failure, etc.
 *
 * This is a retryable error — the handler will retry up to MAX_RETRIES times.
 */
public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message) {
        super(message);
    }

    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
