package com.pspswitch.tpapegress.dispatcher;

import com.pspswitch.tpapegress.dispatcher.handler.WebhookEventHandler;
import com.pspswitch.tpapegress.exception.UnknownEventTypeException;
import com.pspswitch.tpapegress.model.event.EventType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves the correct {@link WebhookEventHandler} for a given {@link EventType}.
 *
 * ADR-001: Polymorphic handler pattern — no if-else or switch dispatch.
 * Adding a new event type = add one new @Component that implements the interface.
 */
@Component
public class EventHandlerFactory {

    private final Map<EventType, WebhookEventHandler> registry;

    public EventHandlerFactory(List<WebhookEventHandler> handlers) {
        this.registry = handlers.stream()
                .collect(Collectors.toMap(WebhookEventHandler::supportedType, h -> h));
    }

    /**
     * @throws UnknownEventTypeException if no handler is registered for the given type (including null)
     */
    public WebhookEventHandler getHandler(EventType type) {
        return Optional.ofNullable(registry.get(type))
                .orElseThrow(() -> new UnknownEventTypeException(type));
    }
}
