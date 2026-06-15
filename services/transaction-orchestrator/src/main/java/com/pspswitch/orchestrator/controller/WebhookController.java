package com.pspswitch.orchestrator.controller;

import com.pspswitch.orchestrator.model.CbsCallbackPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook Controller — receives callbacks from CBS.
 *
 * Endpoints:
 * POST /api/v1/webhook/cbs — CBS credit confirmation (informational, COLLECT
 * only)
 */
@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    public WebhookController() {
    }

    /**
     * POST /api/v1/webhook/cbs
     *
     * Receives CBS credit confirmation — informational only (COLLECT flow).
     * By the time this arrives, the transaction is already in SUCCESS state.
     */
    @PostMapping("/cbs")
    public ResponseEntity<Map<String, String>> handleCbsCallback(@RequestBody CbsCallbackPayload payload) {
        log.info("[WEBHOOK] tid={} | CBS_CONFIRMATION_RECEIVED | status={}",
                payload.getTid(), payload.getStatus());
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
