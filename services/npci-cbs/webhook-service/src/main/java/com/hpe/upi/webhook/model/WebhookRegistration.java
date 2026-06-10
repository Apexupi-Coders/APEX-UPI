package com.hpe.upi.webhook.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WebhookRegistration — stores a registered webhook endpoint.
 *
 * Each registration has:
 *  - A unique ID
 *  - The target URL to POST to
 *  - A secret for HMAC-SHA256 signature (so recipient can verify authenticity)
 *  - List of events to subscribe to (SUCCESS, REVERSED, DEBIT_FAILED, ALL)
 *  - Owner identifier (e.g. PSP app name, merchant ID)
 */
public class WebhookRegistration {

    private String id;
    private String targetUrl;
    private String secret;
    private List<String> subscribedEvents;  // e.g. ["SUCCESS", "REVERSED"]
    private String owner;                   // e.g. "GPay-PSP", "MerchantXYZ"
    private boolean active;
    private Instant registeredAt;

    public WebhookRegistration() {}

    public WebhookRegistration(String targetUrl, String secret,
                                List<String> subscribedEvents, String owner) {
        this.id = UUID.randomUUID().toString();
        this.targetUrl = targetUrl;
        this.secret = secret;
        this.subscribedEvents = subscribedEvents;
        this.owner = owner;
        this.active = true;
        this.registeredAt = Instant.now();
    }

    public boolean isInterestedIn(String eventStatus) {
        if (subscribedEvents == null || subscribedEvents.isEmpty()) return false;
        return subscribedEvents.contains("ALL") || subscribedEvents.contains(eventStatus);
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public List<String> getSubscribedEvents() { return subscribedEvents; }
    public void setSubscribedEvents(List<String> subscribedEvents) { this.subscribedEvents = subscribedEvents; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
}
