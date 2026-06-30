package com.apexupi.operations.model;

import java.time.Instant;

/**
 * Aggregated health status for a single monitored service.
 */
public class ServiceHealthStatus {

    /**
     * Service name as configured (e.g., "PSP VM", "NPCI VM", "Kafka")
     */
    private String serviceName;

    /**
     * UP, DOWN, or ERROR
     */
    private String status;

    /**
     * Response latency in milliseconds, -1 if unreachable
     */
    private long latencyMs;

    /**
     * Timestamp of the health check
     */
    private Instant timestamp;

    /**
     * HTTP status code returned, null if unreachable
     */
    private Integer httpStatus;

    /**
     * Error message if service is DOWN or ERROR, null otherwise
     */
    private String errorMessage;

    public ServiceHealthStatus() {
    }

    public ServiceHealthStatus(String serviceName, String status, long latencyMs, Instant timestamp, Integer httpStatus, String errorMessage) {
        this.serviceName = serviceName;
        this.status = status;
        this.latencyMs = latencyMs;
        this.timestamp = timestamp;
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}