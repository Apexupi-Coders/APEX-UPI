package com.pspswitch.tpapegress.dispatcher.handler;

import com.pspswitch.tpapegress.model.event.EventType;
import com.pspswitch.tpapegress.model.event.SwitchCompletedEvent;

/**
 * Polymorphic handler interface (ADR-001).
 * One implementation per event type — no if-else in the dispatcher.
 */
public interface WebhookEventHandler {

    /** The event type this handler is responsible for. */
    EventType supportedType();

    /** Process the event: config lookup → payload build → HTTP POST → delivery log. */
    void handle(SwitchCompletedEvent event);
}
