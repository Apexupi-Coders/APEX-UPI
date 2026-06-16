package com.pspswitch.tpapegress.dispatcher;

import com.pspswitch.tpapegress.model.event.SwitchCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates webhook dispatch: resolves handler via factory, delegates handle().
 * Does NOT catch or swallow exceptions — propagates upward to the Kafka consumer.
 */
@Service
@Slf4j
public class WebhookDispatcherService {

    private final EventHandlerFactory factory;

    public WebhookDispatcherService(EventHandlerFactory factory) {
        this.factory = factory;
    }

    /**
     * Dispatch a completed switch event to the appropriate handler.
     *
     * @param event the Kafka event envelope
     */
    public void dispatch(SwitchCompletedEvent event) {
        log.info("Dispatching event eventId={} eventType={} tpapId={}",
                event.getEventId(), event.getEventType(), event.getTpapId());

        factory.getHandler(event.getEventType()).handle(event);
    }
}
