package com.pspswitch.ledger.consumer;

import com.pspswitch.ledger.service.TransactionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class NpciResponseEventConsumer {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String RETRY_HEADER = "x-retry-count";

    private final TransactionLedgerService ledgerService;

    @KafkaListener(topics = "${kafka.topics.npci-inbound-response}", groupId = "ledger-service")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("[KAFKA] Consumed npci-inbound-response | key={}", record.key());
        try {
            ledgerService.recordNpciResponseEvent(record.value());
            ack.acknowledge();
        } catch (Exception e) {
            int retryCount = extractRetryCount(record);
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                log.error("[KAFKA] Max retries exhausted for npci-inbound-response | key={} | attempts={}",
                        record.key(), retryCount, e);
                ack.acknowledge();
            } else {
                log.warn("[KAFKA] Processing failed, will retry | key={} | attempt={}/{}",
                        record.key(), retryCount + 1, MAX_RETRY_ATTEMPTS, e);
                ack.nack(1000);
            }
        }
    }

    private int extractRetryCount(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(RETRY_HEADER);
        if (header == null) return 0;
        try {
            return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
