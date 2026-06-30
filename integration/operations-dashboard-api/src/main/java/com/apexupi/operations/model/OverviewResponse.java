package com.apexupi.operations.model;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated overview response containing health status of all monitored services.
 */
public class OverviewResponse {

    /**
     * Timestamp when the overview was generated
     */
    private Instant timestamp;

    /**
     * List of all monitored service health statuses
     */
    private List<ServiceHealthStatus> services;

    /**
     * Overall system health status: UP, DEGRADED, DOWN, or UNKNOWN
     */
    private String overallStatus;

    /**
     * Count of services that are UP
     */
    private int healthyCount;

    /**
     * Count of services that are DOWN or ERROR
     */
    private int unhealthyCount;

    /**
     * Count of services that are DEGRADED
     */
    private int degradedCount;

    /**
     * Total number of monitored services
     */
    private int totalCount;

    public OverviewResponse() {
    }

    public OverviewResponse(Instant timestamp, List<ServiceHealthStatus> services, String overallStatus, int healthyCount, int unhealthyCount, int degradedCount, int totalCount) {
        this.timestamp = timestamp;
        this.services = services;
        this.overallStatus = overallStatus;
        this.healthyCount = healthyCount;
        this.unhealthyCount = unhealthyCount;
        this.degradedCount = degradedCount;
        this.totalCount = totalCount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<ServiceHealthStatus> getServices() {
        return services;
    }

    public void setServices(List<ServiceHealthStatus> services) {
        this.services = services;
    }

    public int getHealthyCount() {
        return healthyCount;
    }

    public void setHealthyCount(int healthyCount) {
        this.healthyCount = healthyCount;
    }

    public int getUnhealthyCount() {
        return unhealthyCount;
    }

    public void setUnhealthyCount(int unhealthyCount) {
        this.unhealthyCount = unhealthyCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public int getDegradedCount() {
        return degradedCount;
    }

    public void setDegradedCount(int degradedCount) {
        this.degradedCount = degradedCount;
    }
}
