package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.adapter.CbsAdapter;
import com.pspswitch.orchestrator.adapter.LedgerService;
import com.pspswitch.orchestrator.adapter.NpciAdapter;
import com.pspswitch.orchestrator.adapter.NotificationService;
import com.pspswitch.orchestrator.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class NpciCallbackHandler {
    private static final Logger log = LoggerFactory.getLogger(NpciCallbackHandler.class);

    private final TransactionStateService stateService;
    private final IdempotencyService idempotencyService;
    private final CbsAdapter cbsAdapter;
    private final NpciAdapter npciAdapter;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutFutures = new ConcurrentHashMap<>();

    public NpciCallbackHandler(TransactionStateService stateService,
                               IdempotencyService idempotencyService,
                               @Lazy CbsAdapter cbsAdapter,
                               @Lazy NpciAdapter npciAdapter,
                               LedgerService ledgerService,
                               NotificationService notificationService) {
        this.stateService = stateService;
        this.idempotencyService = idempotencyService;
        this.cbsAdapter = cbsAdapter;
        this.npciAdapter = npciAdapter;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
    }

    public void registerTimeoutFuture(String tid, ScheduledFuture<?> future) {
        timeoutFutures.put(tid, future);
    }

    public void handleNpciResponse(NpciInboundResponseEvent responseEvent) {
        String tid = responseEvent.getTxnId();
        TransactionContext context = stateService.getByTid(tid);

        if (context == null) {
            log.warn("[NPCI_CALLBACK] tid={} | NPCI callback for unknown transaction", tid);
            return;
        }

        ScheduledFuture<?> timeoutFuture = timeoutFutures.remove(tid);
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }

        if ("SUCCESS".equals(responseEvent.getResult())) {
            log.info("[NPCI_CALLBACK] tid={} | NPCI_SUCCESS | msgId={}",
                    tid, responseEvent.getMsgId());

            String arn = responseEvent.getMsgId();
            if (arn != null && arn.length() > 20) {
                arn = arn.substring(0, 20);
            }
            context.setApprovalRefNo(arn);
            context.setResponseCode("00");

            if (context.getFlowDirection() == FlowDirection.COLLECT) {
                processCbsCreditAsync(context);
            } else {
                completeSendFlow(context);
            }

        } else if ("TIMEOUT".equals(responseEvent.getResult())) {
            log.info("[NPCI_CALLBACK] tid={} | NPCI_TIMEOUT", tid);
            context.setState(TransactionState.UNKNOWN);
            context.setFailureReason("NPCI Timeout");
            stateService.update(context);
            
            notificationService.notifyFailure(tid, context.getPa(), context.getFailureReason());
        } else {
            log.info("[NPCI_CALLBACK] tid={} | NPCI_FAILED | errCode={}", tid, responseEvent.getErrCode());

            context.setState(TransactionState.FAILED);
            context.setResponseCode(responseEvent.getErrCode());
            context.setFailureReason("NPCI rejected: errCode=" + responseEvent.getErrCode());
            stateService.update(context);

            String key = idempotencyService.buildKey(context.getTr(), context.getPa());
            idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));

            notificationService.notifyFailure(tid, context.getPa(), context.getFailureReason());
        }
    }

    private void completeSendFlow(TransactionContext context) {
        String tid = context.getTid();
        ledgerService.record(context, context.getApprovalRefNo());
        context.setState(TransactionState.SUCCESS);
        stateService.update(context);
        String key = idempotencyService.buildKey(context.getTr(), context.getPa());
        idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));
        notificationService.notify(tid, context.getPa(), context.getAm(), "SUCCESS");
        log.info("[ORCHESTRATOR] tid={} | SEND | COMPLETE | Final state=SUCCESS", tid);
    }

    @Async("orchestratorExecutor")
    public void processCbsCreditAsync(TransactionContext context) {
        String tid = context.getTid();
        try {
            log.info("[CBS_ADAPTER] tid={} | REST_CALL_SENT | payee={} | amount={} | mid={}",
                    tid, context.getPa(), context.getAm(), context.getMid());
            boolean cbsSuccess = cbsAdapter.creditPayee(
                    tid, context.getPa(), context.getAm(),
                    context.getMid(), context.getMsid(), context.getMtid());

            if (cbsSuccess) {
                log.info("[CBS_ADAPTER] tid={} | CREDIT_SUCCESS | payee={} credited {}",
                        tid, context.getPa(), context.getAm());
                ledgerService.record(context, context.getApprovalRefNo());
                context.setState(TransactionState.SUCCESS);
                stateService.update(context);
                String key = idempotencyService.buildKey(context.getTr(), context.getPa());
                idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));
                notificationService.notify(tid, context.getPa(), context.getAm(), "SUCCESS");
                log.info("[ORCHESTRATOR] tid={} | COLLECT | COMPLETE | Final state=SUCCESS", tid);
            } else {
                log.info("[CBS_ADAPTER] tid={} | CREDIT_FAILED | Triggering compensation", tid);
                performCompensation(context);
            }
        } catch (Exception e) {
            log.error("[ORCHESTRATOR] tid={} | CBS processing error: {}", tid, e.getMessage(), e);
            performCompensation(context);
        }
    }

    private void performCompensation(TransactionContext context) {
        String tid = context.getTid();
        log.info("[COMPENSATION] tid={} | REVERSAL_SENT | amount={}", tid, context.getAm());
        npciAdapter.reversal(tid, context.getAm());
        context.setState(TransactionState.COMPENSATED);
        context.setFailureReason("CBS credit failed — reversal sent to NPCI");
        stateService.update(context);
        String key = idempotencyService.buildKey(context.getTr(), context.getPa());
        idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));
        notificationService.notifyCompensation(tid, context.getPa(), context.getAm());
        log.info("[COMPENSATION] tid={} | state=COMPENSATED | Flow ended", tid);
    }
}
