package com.apexupi.operations.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Read-only service health aggregation for the dashboard.
 */
@RestController
public class HealthReadOnlyController {

    @GetMapping("/api/v1/ops/health")
    public Map<String, Object> health() {
        // Placeholder response. Later: wire real health checks.
        return Map.of(
                "timestamp", Instant.now().toString(),
                "services", Map.of(
                        "TPAP", Map.of("status", "UNKNOWN"),
                        "Orchestrator", Map.of("status", "UNKNOWN"),
                        "NPCI Adapter", Map.of("status", "UNKNOWN"),
                        "Kafka", Map.of("status", "UNKNOWN"),
                        "Redis", Map.of("status", "UNKNOWN"),
                        "PostgreSQL", Map.of("status", "UNKNOWN")
                )
        );
    }
}

