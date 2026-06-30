package com.apexupi.operations.api;

import com.apexupi.operations.model.OverviewResponse;
import com.apexupi.operations.service.HealthAggregationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only overview endpoint that aggregates health status from all monitored services.
 */
@RestController
@RequestMapping("/api/v1/ops")
public class OverviewReadOnlyController {

    private final HealthAggregationService healthAggregationService;

    public OverviewReadOnlyController(HealthAggregationService healthAggregationService) {
        this.healthAggregationService = healthAggregationService;
    }

    /**
     * GET /api/v1/ops/overview
     * 
     * Returns aggregated health status of all configured backend services.
     * Calls every service concurrently and never stops if one service fails.
     * 
     * @return OverviewResponse with service health statuses
     */
    @GetMapping("/overview")
    public ResponseEntity<OverviewResponse> overview() {
        OverviewResponse response = healthAggregationService.aggregateHealth();
        return ResponseEntity.ok(response);
    }
}