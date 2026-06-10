package com.hpe.upi.webhook.controller;

import com.hpe.upi.webhook.model.WebhookRegistration;
import com.hpe.upi.webhook.service.WebhookDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * WebhookController — REST API for managing webhook registrations.
 *
 * Endpoints:
 *   POST   /api/webhooks/register     — register a new webhook URL
 *   GET    /api/webhooks              — list all registrations
 *   DELETE /api/webhooks/{id}         — deactivate a registration
 *   GET    /api/webhooks/{id}/logs    — view delivery logs for a registration
 *   GET    /api/webhooks/health       — health check
 *
 * Example registration request:
 * {
 *   "targetUrl": "https://my-psp-app.com/upi/callback",
 *   "secret": "my-hmac-secret-key",
 *   "subscribedEvents": ["SUCCESS", "REVERSED"],
 *   "owner": "GPay-PSP"
 * }
 */
@RestController
@RequestMapping("/api/webhooks")
@CrossOrigin(origins = "*")
public class WebhookController {

    private final WebhookDeliveryService deliveryService;

    public WebhookController(WebhookDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, Object> request) {

        String targetUrl = (String) request.get("targetUrl");
        String secret    = (String) request.getOrDefault("secret", "default-secret");
        String owner     = (String) request.getOrDefault("owner", "unknown");

        @SuppressWarnings("unchecked")
        List<String> events = (List<String>) request.getOrDefault(
            "subscribedEvents", List.of("SUCCESS", "REVERSED", "DEBIT_FAILED"));

        if (targetUrl == null || targetUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "targetUrl is required"));
        }

        WebhookRegistration reg = new WebhookRegistration(targetUrl, secret, events, owner);
        deliveryService.register(reg);

        return ResponseEntity.ok(Map.of(
            "id",               reg.getId(),
            "targetUrl",        reg.getTargetUrl(),
            "owner",            reg.getOwner(),
            "subscribedEvents", reg.getSubscribedEvents(),
            "active",           reg.isActive(),
            "registeredAt",     reg.getRegisteredAt().toString(),
            "message",          "Webhook registered successfully"
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listRegistrations() {
        List<WebhookRegistration> regs = deliveryService.getAllRegistrations();
        return ResponseEntity.ok(Map.of(
            "registrations", regs,
            "total",         regs.size()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deactivate(@PathVariable String id) {
        boolean deactivated = deliveryService.deactivate(id);
        if (!deactivated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("id", id, "active", false, "message", "Webhook deactivated"));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<Map<String, Object>> getDeliveryLogs(@PathVariable String id) {
        List<Map<String, Object>> logs = deliveryService.getDeliveryLogs(id);
        return ResponseEntity.ok(Map.of("registrationId", id, "logs", logs, "total", logs.size()));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "Webhook-Service"));
    }
}
