package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.TransactionContext;
import com.pspswitch.orchestrator.model.TransactionState;
import com.pspswitch.orchestrator.model.TransactionResponse;
import com.pspswitch.orchestrator.service.IdempotencyService;
import com.pspswitch.orchestrator.service.TransactionStateService;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka Consumer — consumes NPCI inbound responses and drives state transitions.
 */
@Component
public class NpciInboundResponseConsumer {

    private static final Logger log = LoggerFactory.getLogger(NpciInboundResponseConsumer.class);

    private final ObjectMapper objectMapper;
    private final TransactionStateService stateService;
    private final IdempotencyService idempotencyService;

    @Value("${app.kafka.topics.npci-inbound-response:npci.inbound.response}")
    private String inboundTopic;

    @Value("${spring.kafka.consumer.group-id:psp-orchestrator}")
    private String groupId;

    public NpciInboundResponseConsumer(ObjectMapper objectMapper,
            TransactionStateService stateService,
            IdempotencyService idempotencyService) {
        this.objectMapper = objectMapper;
        this.stateService = stateService;
        this.idempotencyService = idempotencyService;
    }

    @KafkaListener(topics = { "${app.kafka.topics.npci-inbound-response:npci.inbound.response}" },
            groupId = "${spring.kafka.consumer.group-id:psp-orchestrator}")
    public void consume(String message) {
        try {
            NpciInboundResponseEvent event = objectMapper.readValue(message, NpciInboundResponseEvent.class);
            String txnId = event.getTxnId();
            log.info("[ORCH_INBOUND] rawPayload={} | parsed.txnId={} parsed.result={} parsed.errCode={}",
                    message, txnId, event.getResult(), event.getErrCode());


            // DEBUG: integration tests simulate adapter callbacks via in-process adapters,
            // so inbound Kafka messages may be disabled. If state isn't found, log the current cache.
            TransactionContext ctx = stateService.getByTid(txnId);

            if (ctx == null) {
                log.warn("[ORCH_INBOUND] Unknown txnId={} | topic={} | dropping", txnId, inboundTopic);
                return;
            }

            if (ctx.getState() != TransactionState.SUBMITTED) {
                log.info("[ORCH_INBOUND] txnId={} | state={} | ignoring inbound response because not SUBMITTED",
                        txnId, ctx.getState());
                return;
            }

            // Adapter shape mapping:
            // - success if result is SUCCESS or DEEMED
            // - failure otherwise (result=FAILURE/TIMEOUT)
            boolean success = false;
            String result = event.getResult();
            if (result != null) {
                success = "SUCCESS".equalsIgnoreCase(result) || "DEEMED".equalsIgnoreCase(result);
            }
            // Legacy/orchestrator shape mapping
            if (!success) {
                success = "00".equals(event.getResponseCode()) || "SUCCESS".equalsIgnoreCase(event.getStatus());
            }

            log.info("[ORCH_INBOUND] txnId={} | adapter.result={} adapter.errCode={} | legacy.responseCode={} legacy.status={} ",
                    txnId, event.getResult(), event.getErrCode(), event.getResponseCode(), event.getStatus());

            if (success) {
                // approvalRefNo isn't provided by adapter in this demo; keep existing or set a safe value
                if (event.getApprovalRefNo() != null) {
                    ctx.setApprovalRefNo(event.getApprovalRefNo());
                }
                if (event.getResponseCode() != null) {
                    ctx.setResponseCode(event.getResponseCode());
                } else if (event.getErrCode() != null) {
                    ctx.setResponseCode(event.getErrCode());
                } else {
                    ctx.setResponseCode("00");
                }
                ctx.setState(TransactionState.SUCCESS);
                ctx.setFailureReason(null);


                stateService.update(ctx);

                String idempotencyKey = idempotencyService.buildKey(ctx.getTr(), ctx.getPa());
                idempotencyService.cacheResponse(idempotencyKey, TransactionResponse.fromContext(ctx));

                log.info("[STATE_CHANGE] txnId={} | SUBMITTED → SUCCESS", txnId);

            } else {
                if (event.getResponseCode() != null) {
                    ctx.setResponseCode(event.getResponseCode());
                } else if (event.getErrCode() != null) {
                    ctx.setResponseCode(event.getErrCode());
                }

                ctx.setState(TransactionState.FAILED);

                String reason = event.getResponseCode() != null ? event.getResponseCode() : String.valueOf(event.getErrCode());
                ctx.setFailureReason("NPCI rejected: responseCode/errCode=" + reason);


                stateService.update(ctx);

                String idempotencyKey = idempotencyService.buildKey(ctx.getTr(), ctx.getPa());
                idempotencyService.cacheResponse(idempotencyKey, TransactionResponse.fromContext(ctx));

                log.info("[STATE_CHANGE] txnId={} | SUBMITTED → FAILED", txnId);
            }

        } catch (Exception e) {
            log.error("[ORCH_INBOUND] Failed to consume inbound response: {}", e.getMessage(), e);
            // Let Kafka retry; DLQ can be configured at broker level
        }
    }
}
