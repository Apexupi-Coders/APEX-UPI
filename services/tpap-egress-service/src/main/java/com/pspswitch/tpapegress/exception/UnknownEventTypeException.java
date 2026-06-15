package com.pspswitch.tpapegress.exception;

/**
 * Thrown when the EventHandlerFactory cannot resolve a handler
 * for the given event type (including null).
 */
public class UnknownEventTypeException extends RuntimeException {

    public UnknownEventTypeException(Object eventType) {
        super("No handler registered for event type: " + eventType);
    }
}
