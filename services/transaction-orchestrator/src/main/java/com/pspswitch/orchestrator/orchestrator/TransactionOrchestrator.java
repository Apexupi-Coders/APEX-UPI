package com.pspswitch.orchestrator.orchestrator;

import com.pspswitch.orchestrator.adapter.NpciAdapter;
import com.pspswitch.orchestrator.controller.WebhookController;
import com.pspswitch.orchestrator.model.*;
import com.pspswitch.orchestrator.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Transaction Orchestrator — implements the orchestration saga.
 *
 * Supports dual-direction flows:
 * SEND (Payer Side): Steps 1-5 sync + Steps 6-8 async (no CBS)
 * COLLECT (Receiver Side): Steps 1-5 sync + Steps 6-8 async (CBS credit +
 * compensation)
 *
 * Step 1: Idempotency check (composite key = tr::pa)
 * Step 2: TID generation (PSP- + first 8 chars of UUID uppercase)
 * Step 3: Mode preprocessing (mode 04/05/16 → requiresPasscode, flowType)
 * Step 4: Validation (9 sequential rules)
 * Step 5: Write PENDING state, return HTTP 202
 * Step 6: NPCI REST call (async) + register timeout
 * Step 7: NPCI webhook callback (handled by WebhookController)
 * Step 8: Completion — branches on flowDirection:
 * SEND: Ledger + SUCCESS + Notify TPAP
 * COLLECT: CBS credit + Ledger + SUCCESS (or COMPENSATED on CBS failure)
 *
 * Timeout: ScheduledExecutorService checks after 5 seconds.
 * If state is still SUBMITTED → mark UNKNOWN.
 */
@Service
public class TransactionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransactionOrchestrator.class);
    private static final long NPCI_TIMEOUT_SECONDS = 5;

    private final IdempotencyService idempotencyService;
    private final TransactionStateService stateService;
    private final ModePreprocessingService modePreprocessingService;
    private final ValidationService validationService;
    private final NpciAdapter npciAdapter;
    private final WebhookController webhookController;

    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(2);

    public TransactionOrchestrator(IdempotencyService idempotencyService,
            TransactionStateService stateService,
            ModePreprocessingService modePreprocessingService,
            ValidationService validationService,
            NpciAdapter npciAdapter,
            WebhookController webhookController) {
        this.idempotencyService = idempotencyService;
        this.stateService = stateService;
        this.modePreprocessingService = modePreprocessingService;
        this.validationService = validationService;
        this.npciAdapter = npciAdapter;
        this.webhookController = webhookController;
    }

    /**
     * Orchestrates Steps 1-5 synchronously, then triggers Steps 6-10 async.
     *
     * @return OrchestratorResult containing the response and whether it's a
     *         duplicate
     */
    public OrchestratorResult orchestrate(UpiPaymentRequest request) {

        // ═══════════════════════════════════════════════════════
        // STEP 1 — IDEMPOTENCY CHECK
        // ═══════════════════════════════════════════════════════
        String compositeKey = idempotencyService.buildKey(request.getTr(), request.getPa());

        if (!idempotencyService.claimSlot(compositeKey)) {
            // DUPLICATE — return cached response
            TransactionResponse cached = idempotencyService.getCachedResponse(compositeKey);
            if (cached != null) {
                return new OrchestratorResult(cached, true);
            }
            // Still PROCESSING — return a processing indicator
            TransactionResponse processingResp = new TransactionResponse();
            processingResp.setState("PROCESSING");
            processingResp.setMessage("Transaction is still being processed");
            return new OrchestratorResult(processingResp, true);
        }

        // ═══════════════════════════════════════════════════════
        // STEP 2 — TID EXTRACTION / GENERATION
        // ═══════════════════════════════════════════════════════
        // In UPI, the TID is typically sent by the TPAP/Ingress layout.
        String tid = request.getTid();
        if (tid == null || tid.trim().isEmpty()) {
            tid = "PSP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            log.warn("[ORCHESTRATOR] tid missing from TPAP. Generated fallback tid={} | tr={}", tid, request.getTr());
        } else {
            log.info("[ORCHESTRATOR] tid={} | received from TPAP | tr={}", tid, request.getTr());
        }

        // ═══════════════════════════════════════════════════════
        // STEP 3 — MODE PREPROCESSING
        // ═══════════════════════════════════════════════════════
        PreprocessingContext ppCtx = modePreprocessingService.process(request);
        log.info("[MODE] tid={} | mode={} | requiresPasscode={} | flowType={}",
                tid, request.getMode(), ppCtx.isRequiresPasscode(), ppCtx.getFlowType());

        // ═══════════════════════════════════════════════════════
        // STEP 4 — VALIDATION
        // ═══════════════════════════════════════════════════════
        // If validation fails, ValidationException is thrown and caught by
        // GlobalExceptionHandler → HTTP 400 + FAILED state.
        // Idempotency key remains as PROCESSING — next attempt will get duplicate.
        validationService.validate(request, tid);

        // ═══════════════════════════════════════════════════════
        // STEP 5 — WRITE PENDING STATE
        // ═══════════════════════════════════════════════════════
        TransactionContext context = buildContext(request, tid, ppCtx);
        stateService.save(context);

        log.info("[ORCHESTRATOR] tid={} | tr={} | mode={} | state=PENDING | am={}",
                tid, request.getTr(), request.getMode(), request.getAm());

        // Build the initial PENDING response (returned as HTTP 202)
        TransactionResponse pendingResponse = TransactionResponse.fromContext(context);
        pendingResponse.setMessage("Processing. Poll GET /api/v1/txn/" + tid);

        // ═══════════════════════════════════════════════════════
        // STEPS 6-10 — ASYNC EXECUTION
        // ═══════════════════════════════════════════════════════
        executeAsyncSaga(context);

        return new OrchestratorResult(pendingResponse, false);
    }

    /**
     * Executes Steps 6-8 asynchronously.
     * Step 6: NPCI REST call + timeout registration
     * Steps 7-8: Handled by WebhookController when NPCI webhook arrives
     */
    @Async("orchestratorExecutor")
    public void executeAsyncSaga(TransactionContext context) {
        String tid = context.getTid();

        try {
            // STEP 6 — NPCI REST CALL
            // This blocks for 800ms (simulated network latency),
            // then fires the webhook callback async after 1500ms
            npciAdapter.forward(tid);

            // After NPCI REST call returns SUBMITTED
            context.setState(TransactionState.SUBMITTED);
            stateService.update(context);

            // Register timeout: if webhook hasn't arrived in 5 seconds → UNKNOWN
            ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
                TransactionContext ctx = stateService.getByTid(tid);
                if (ctx != null && ctx.getState() == TransactionState.SUBMITTED) {
                    log.info("[ORCHESTRATOR] tid={} | UNKNOWN | No NPCI webhook received within timeout", tid);
                    ctx.setState(TransactionState.UNKNOWN);
                    ctx.setFailureReason("NPCI webhook timeout — no response within " + NPCI_TIMEOUT_SECONDS + "s");
                    stateService.update(ctx);

                    String key = idempotencyService.buildKey(ctx.getTr(), ctx.getPa());
                    idempotencyService.cacheResponse(key, TransactionResponse.fromContext(ctx));
                }
            }, NPCI_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Register the timeout future so WebhookController can cancel it
            webhookController.registerTimeoutFuture(tid, timeoutFuture);

            // Steps 7-10 are triggered by the NPCI webhook callback
            // (handled in WebhookController.handleNpciCallback)

        } catch (Exception e) {
            log.error("[ORCHESTRATOR] tid={} | Async saga failed: {}", tid, e.getMessage(), e);
            context.setState(TransactionState.FAILED);
            context.setFailureReason("Internal error: " + e.getMessage());
            stateService.update(context);

            String key = idempotencyService.buildKey(context.getTr(), context.getPa());
            idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));
        }
    }

    /**
     * Builds the TransactionContext from the request, generated tid, and
     * preprocessing result.
     */
    private TransactionContext buildContext(UpiPaymentRequest request, String tid, PreprocessingContext ppCtx) {
        TransactionContext ctx = new TransactionContext();
        ctx.setTid(tid);
        ctx.setTr(request.getTr());
        ctx.setPa(request.getPa());
        ctx.setPn(request.getPn());
        ctx.setMc(request.getMc());
        ctx.setAm(request.getAm());
        ctx.setMam(request.getMam());
        ctx.setCu(request.getCu());
        ctx.setMode(request.getMode());
        ctx.setMid(request.getMid());
        ctx.setMsid(request.getMsid());
        ctx.setMtid(request.getMtid());
        ctx.setRequiresPasscode(ppCtx.isRequiresPasscode());
        ctx.setFlowType(ppCtx.getFlowType());
        ctx.setState(TransactionState.PENDING);
        ctx.setCreatedAt(Instant.now());
        ctx.setUpdatedAt(Instant.now());

        // Dual-direction: parse flowDirection from request (default SEND)
        if ("COLLECT".equalsIgnoreCase(request.getFlowDirection())) {
            ctx.setFlowDirection(FlowDirection.COLLECT);
        } else {
            ctx.setFlowDirection(FlowDirection.SEND);
        }

        return ctx;
    }

    /**
     * Result container for the orchestrate() method.
     * Carries both the response and a duplicate flag.
     */
    public static class OrchestratorResult {
        private final TransactionResponse response;
        private final boolean duplicate;

        public OrchestratorResult(TransactionResponse response, boolean duplicate) {
            this.response = response;
            this.duplicate = duplicate;
        }

        public TransactionResponse getResponse() {
            return response;
        }

        public boolean isDuplicate() {
            return duplicate;
        }
    }
}
