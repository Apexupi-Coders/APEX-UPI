package com.psp.npci.adapter.controller;

import com.psp.npci.adapter.service.IdempotencyService;
import com.psp.npci.adapter.service.NpciAdapterService;
import com.psp.npci.adapter.service.XmlBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook controller — REST endpoints that Mock NPCI calls back on.
 *
 * <h2>Ack-first pattern (critical)</h2>
 * <p>
 * Every handler:
 * <ol>
 * <li>Checks idempotency (discard duplicate callbacks)</li>
 * <li>Builds Ack XML</li>
 * <li>Dispatches {@code @Async} background processing</li>
 * <li>Returns HTTP 200 + Ack XML synchronously to NPCI</li>
 * </ol>
 *
 * <p>
 * NPCI's callback timeout (~2–5 s) makes it mandatory that the HTTP response
 * is returned before any Redis/Kafka/parsing work happens. The {@code @Async}
 * method in {@link NpciAdapterService} runs on {@code npciAsyncExecutor} after
 * the servlet thread has already completed the response.
 *
 * <h2>No Redis</h2>
 * <p>
 * Idempotency is handled by {@link IdempotencyService} (in-memory map).
 * Status is NOT stored anywhere — the Orchestrator reacts to Kafka events.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class NpciWebhookController {

        private final NpciAdapterService npciAdapterService;
        private final IdempotencyService idempotencyService;
        private final XmlBuilderService xmlBuilderService;

        // ─────────────────────────────────────────────────────────────────────────
        // Flow A — NPCI → Adapter : RespPay callback
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Receives the async RespPay callback from Mock NPCI after a PAY request.
         *
         * <p>
         * URL: {@code POST /upi/RespPay/1.0/urn:txnid/{txnId}}
         */
        @PostMapping(value = "/upi/RespPay/1.0/urn:txnid/{txnId}", consumes = { MediaType.APPLICATION_XML_VALUE,
                        MediaType.TEXT_XML_VALUE, MediaType.ALL_VALUE }, produces = MediaType.APPLICATION_XML_VALUE)
        public ResponseEntity<String> handleRespPay(
                        @PathVariable String txnId,
                        @RequestBody String xmlBody,
                        @RequestHeader(value = "X-UPI-Signature", required = false) String sigHeader) {

                log.info("[NPCI-ADAPTER] RespPay webhook hit | txnId={}", txnId);

                // Idempotency: drop duplicate callbacks immediately
                if (idempotencyService.isAlreadyProcessed(txnId)) {
                        log.info("[NPCI-ADAPTER] Duplicate RespPay — discarded | txnId={}", txnId);
                        return ack(txnId);
                }

                // Trigger async processing (runs AFTER this method returns HTTP 200)
                npciAdapterService.processRespPayAsync(txnId, xmlBody, sigHeader);

                log.info("[NPCI-ADAPTER] Ack sent to NPCI | txnId={}", txnId);
                return ack(txnId);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Flow C — NPCI → Adapter : Inbound Collect / Credit
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Receives an NPCI-initiated collect or credit request.
         *
         * <p>
         * URL: {@code POST /upi/ReqPay/1.0/urn:txnid/{txnId}}
         */
        @PostMapping(value = "/upi/ReqPay/1.0/urn:txnid/{txnId}", consumes = { MediaType.APPLICATION_XML_VALUE,
                        MediaType.TEXT_XML_VALUE, MediaType.ALL_VALUE }, produces = MediaType.APPLICATION_XML_VALUE)
        public ResponseEntity<String> handleInboundCollect(
                        @PathVariable String txnId,
                        @RequestBody String xmlBody,
                        @RequestHeader(value = "X-UPI-Signature", required = false) String sigHeader) {

                log.info("[NPCI-ADAPTER] Inbound collect from NPCI | txnId={}", txnId);

                if (idempotencyService.isAlreadyProcessed(txnId)) {
                        log.info("[NPCI-ADAPTER] Duplicate collect — discarded | txnId={}", txnId);
                        return ack(txnId);
                }

                npciAdapterService.processInboundCollectAsync(txnId, xmlBody, sigHeader);

                log.info("[NPCI-ADAPTER] Ack sent for inbound collect | txnId={}", txnId);
                return ack(txnId);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // Helper
        // ─────────────────────────────────────────────────────────────────────────

        private ResponseEntity<String> ack(String txnId) {
                return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_XML)
                                .body(xmlBuilderService.buildAck(txnId));
        }
}
