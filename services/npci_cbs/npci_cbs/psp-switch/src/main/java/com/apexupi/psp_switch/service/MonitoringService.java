package com.apexupi.psp_switch.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.apexupi.psp_switch.model.TimelineEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Production-grade Transaction Monitoring & Observability Service.
 * 
 * - Thread-safe timeline per txnId (ConcurrentHashMap + CopyOnWriteArrayList)
 * - AtomicLong metrics counters
 * - Structured logging
 * - Extensible to Redis/Kafka
 * - Non-blocking, zero core business logic impact
 */
@Service
@Slf4j
public class MonitoringService {
    
    // Timelines: txnId -> list of events (thread-safe)
    private final ConcurrentHashMap<String, List<TimelineEvent>> timelines = new ConcurrentHashMap<>();
    
    // Global metrics (thread-safe counters)
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong compensatedCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);
    private final AtomicLong dlqCount = new AtomicLong(0);
    
    /**
     * Add event to transaction timeline.
     * Thread-safe, non-blocking.
     */
    public void addEvent(String txnId, String eventName) {
        TimelineEvent event = new TimelineEvent(eventName, LocalDateTime.now());
        
        timelines.computeIfAbsent(txnId, k -> new CopyOnWriteArrayList<>()).add(event);
        
        // Structured logging
        log.info("[txnId={}] EVENT: {}", txnId, eventName);
        
        // Update metrics based on terminal events
        updateMetrics(eventName);
    }
    
    private void updateMetrics(String eventName) {
    // Only count actual new transactions, not every event
    switch (eventName) {
        case "CREATED":
            totalTransactions.incrementAndGet();
            break;
        case "SUCCESS":
            successCount.incrementAndGet();
            break;
        case "FAILED":
            failedCount.incrementAndGet();
            break;
        case "COMPENSATED":
            compensatedCount.incrementAndGet();
            break;
        case "RETRYING":
            retryCount.incrementAndGet();
            break;
        case "DLQ":
            dlqCount.incrementAndGet();
            break;
    }
}
    
    public List<TimelineEvent> getTimeline(String txnId) {
        return timelines.getOrDefault(txnId, List.of());
    }
    
    /**
     * Get all transactions with current status (derived from last event).
     * Production: paginated, filtered.
     */
    public List<String> getTransactions() {
        return (List<String>) timelines.keySet().stream().collect(Collectors.toList());
    }
    
    /**
     * Aggregated metrics.
     */
    public MonitoringStats getStats() {
        return new MonitoringStats(
            totalTransactions.get(),
            successCount.get(),
            failedCount.get(),
            compensatedCount.get(),
            retryCount.get(),
            dlqCount.get()
        );
    }
    
    /**
     * Failed + compensated transactions.
     */
    public List<String> getFailed() {
        return timelines.keySet().stream()
            .filter(txnId -> {
                var events = getTimeline(txnId);
                return !events.isEmpty() && 
                       (events.get(events.size()-1).getEventName().equals("FAILED") ||
                        events.get(events.size()-1).getEventName().equals("COMPENSATED"));
            })
            .collect(Collectors.toList());
    }
    
    // Inner record for stats response
    public record MonitoringStats(
        long totalTransactions,
        long successCount,
        long failedCount,
        long compensatedCount,
        long retryCount,
        long dlqCount
    ) {}
}

