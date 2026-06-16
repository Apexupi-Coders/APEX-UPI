package com.apexupi.psp_switch.service;

import com.apexupi.psp_switch.model.DlqEntry;
import com.apexupi.psp_switch.queue.PaymentQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class DlqService {

    private final RedisTemplate<String, String> redisTemplate;
    
    private final PaymentQueue paymentQueue;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String DLQ_KEY_PREFIX = "dlq:entry:";
    public DlqService(RedisTemplate<String, String> redisTemplate, PaymentQueue paymentQueue) {
        this.redisTemplate = redisTemplate;
        this.paymentQueue = paymentQueue;
    }

    public void sendToDLQ(DlqEntry entry) {
        try {
            String key = DLQ_KEY_PREFIX + entry.getTxnId();
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(key, json);
            log.info("Sent to DLQ: txnId={}, reason={}", entry.getTxnId(), entry.getFailureReason());
        } catch (Exception e) {
            log.error("Failed to send to DLQ: {}", entry.getTxnId(), e);
        }
    }

    public List<DlqEntry> getAllFailedTransactions() {
        List<DlqEntry> entries = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys(DLQ_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String json = redisTemplate.opsForValue().get(key);
                    if (json != null) {
                        DlqEntry entry = objectMapper.readValue(json, DlqEntry.class);
                        entries.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch DLQ entries", e);
        }
        return entries;
    }

    public void retryFromDLQ(String txnId) {
        try {
            String key = DLQ_KEY_PREFIX + txnId;
            redisTemplate.delete(key);
            paymentQueue.publish(txnId);
            log.info("Retried from DLQ: txnId={}", txnId);
        } catch (Exception e) {
            log.error("Failed to retry from DLQ: {}", txnId, e);
        }
    }

    @PostConstruct
    public void logReady() {
        log.info("DLQ Service initialized with Redis persistence");
    }
}

