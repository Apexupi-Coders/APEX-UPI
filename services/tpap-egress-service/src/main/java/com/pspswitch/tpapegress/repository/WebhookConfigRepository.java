package com.pspswitch.tpapegress.repository;

import com.pspswitch.tpapegress.model.entity.WebhookConfig;
import com.pspswitch.tpapegress.model.event.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {

    /**
     * Look up the webhook configuration for a given TPAP and event type.
     * Returns the row regardless of the active flag — the handler performs
     * a secondary active-check so that the SKIPPED path is testable.
     */
    Optional<WebhookConfig> findByTpapIdAndEventType(String tpapId, EventType eventType);

    /**
     * Convenience alias used throughout the codebase and test fixtures.
     * Delegates to {@link #findByTpapIdAndEventType(String, EventType)}.
     */
    default Optional<WebhookConfig> findActiveConfig(String tpapId, EventType eventType) {
        return findByTpapIdAndEventType(tpapId, eventType);
    }
}
