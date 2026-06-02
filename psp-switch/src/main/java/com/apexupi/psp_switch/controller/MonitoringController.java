package com.apexupi.psp_switch.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.apexupi.psp_switch.model.TimelineEvent;
import com.apexupi.psp_switch.service.MonitoringService;

import lombok.RequiredArgsConstructor;

/**
 * Observability REST APIs.
 * No auth for demo (add @PreAuthorize in prod).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor")
public class MonitoringController {
    
    private final MonitoringService monitoringService;
    
    @GetMapping("/txn/{txnId}/timeline")
    public ResponseEntity<List<TimelineEvent>> getTimeline(@PathVariable String txnId) {
        var timeline = monitoringService.getTimeline(txnId);
        return ResponseEntity.ok(timeline);
    }
    
    @GetMapping("/transactions")
    public ResponseEntity<List<String>> getTransactions() {
        var txns = monitoringService.getTransactions();
        return ResponseEntity.ok(txns);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<MonitoringService.MonitoringStats> getStats() {
        var stats = monitoringService.getStats();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/failed")
    public ResponseEntity<List<String>> getFailed() {
        var failed = monitoringService.getFailed();
        return ResponseEntity.ok(failed);
    }
}
