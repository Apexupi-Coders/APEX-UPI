 package com.apexupi.operations.service;

import com.apexupi.operations.config.ServiceEndpoint;
import com.apexupi.operations.config.ServiceEndpoint.ServiceConfig;
import com.apexupi.operations.model.ControlStatusResponse;
import com.apexupi.operations.model.OverviewResponse;
import com.apexupi.operations.model.ServiceHealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aggregates health checks from all configured backend services.
 * 
 * Calls every service concurrently and never stops if one service fails.
 */
@Service
public class HealthAggregationService {

    private static final Logger log = LoggerFactory.getLogger(HealthAggregationService.class);
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String STATUS_ERROR = "ERROR";

    private final ServiceEndpoint serviceEndpoint;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final String liveControlStatusUrl;

    public HealthAggregationService(ServiceEndpoint serviceEndpoint,
                                   @Value("${app.live.control-status-url:https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/status}") String liveControlStatusUrl) {
        this.serviceEndpoint = serviceEndpoint;
        this.liveControlStatusUrl = liveControlStatusUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.executorService = Executors.newFixedThreadPool(
                Math.min(32, Math.max(4, serviceEndpoint.getEndpoints().size()))
        );
        log.info("HealthAggregationService initialized with live control status URL: {}", liveControlStatusUrl);
    }

    /**
     * Aggregates health status from the live control status API.
     * 
     * @return OverviewResponse containing live system status
     */
    public OverviewResponse aggregateHealth() {
        log.debug("Fetching live control status from {}", liveControlStatusUrl);

        try {
            ControlStatusResponse liveStatus = fetchLiveControlStatus();
            List<ServiceHealthStatus> services = transformToServiceHealthStatuses(liveStatus);
            String overallStatus = determineOverallStatus(liveStatus);

            int healthyCount = (int) services.stream()
                    .filter(s -> STATUS_UP.equalsIgnoreCase(s.getStatus()))
                    .count();
            int unhealthyCount = (int) services.stream()
                    .filter(s -> !STATUS_UP.equalsIgnoreCase(s.getStatus()) && !STATUS_DEGRADED.equalsIgnoreCase(s.getStatus()))
                    .count();
            int degradedCount = (int) services.stream()
                    .filter(s -> STATUS_DEGRADED.equalsIgnoreCase(s.getStatus()))
                    .count();

            log.info("Live status aggregation complete: {} services, {} healthy, {} degraded, {} unhealthy",
                    services.size(), healthyCount, degradedCount, unhealthyCount);

            return new OverviewResponse(
                    Instant.now(),
                    services,
                    overallStatus,
                    healthyCount,
                    unhealthyCount,
                    degradedCount,
                    services.size()
            );
        } catch (Exception ex) {
            log.error("Failed to fetch live control status from {}: {}", liveControlStatusUrl, ex.getMessage());
            return createUnavailableResponse(ex);
        }
    }

    /**
     * Fetches live control status from the external API.
     */
    private ControlStatusResponse fetchLiveControlStatus() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(liveControlStatusUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Live control status API returned status: " + response.statusCode());
        }

        // Parse JSON manually using simple string parsing
        // In production, use Jackson ObjectMapper
        return parseControlStatusResponse(response.body());
    }

    /**
     * Simple JSON parser for ControlStatusResponse.
     * In production, replace with Jackson ObjectMapper.
     */
    private ControlStatusResponse parseControlStatusResponse(String json) {
        ControlStatusResponse response = new ControlStatusResponse();
        
        // Extract toggles
        ControlStatusResponse.Toggles toggles = new ControlStatusResponse.Toggles();
        toggles.setNpciFailureMode(json.contains("\"npciFailureMode\":true"));
        toggles.setCbsFailureMode(json.contains("\"cbsFailureMode\":true"));
        toggles.setNpciWebhookSuppressed(json.contains("\"npciWebhookSuppressed\":true"));
        response.setToggles(toggles);

        // Extract transaction counts
        ControlStatusResponse.TransactionCounts counts = new ControlStatusResponse.TransactionCounts();
        counts.setPENDING(extractIntValue(json, "\"PENDING\":"));
        counts.setSUBMITTED(extractIntValue(json, "\"SUBMITTED\":"));
        counts.setSUCCESS(extractIntValue(json, "\"SUCCESS\":"));
        counts.setFAILED(extractIntValue(json, "\"FAILED\":"));
        counts.setUNKNOWN(extractIntValue(json, "\"UNKNOWN\":"));
        counts.setCOMPENSATED(extractIntValue(json, "\"COMPENSATED\":"));
        response.setTransactionCounts(counts);

        // Extract service sizes
        ControlStatusResponse.ServiceSizes sizes = new ControlStatusResponse.ServiceSizes();
        sizes.setTotalTransactions(extractIntValue(json, "\"totalTransactions\":"));
        sizes.setIdempotencyKeys(extractIntValue(json, "\"idempotencyKeys\":"));
        sizes.setLedgerEntries(extractIntValue(json, "\"ledgerEntries\":"));
        response.setServiceSizes(sizes);

        // Extract infrastructure
        ControlStatusResponse.Infrastructure infra = new ControlStatusResponse.Infrastructure();
        infra.setDatabase(extractStringValue(json, "\"database\":\"", "\""));
        infra.setCache(extractStringValue(json, "\"cache\":\"", "\""));
        infra.setMessaging(extractStringValue(json, "\"messaging\":\"", "\""));
        infra.setCryptoEnabled(json.contains("\"cryptoEnabled\":true"));
        response.setInfrastructure(infra);

        return response;
    }

    private int extractIntValue(String json, String key) {
        int index = json.indexOf(key);
        if (index == -1) return 0;
        int start = index + key.length();
        StringBuilder sb = new StringBuilder();
        while (start < json.length() && Character.isDigit(json.charAt(start))) {
            sb.append(json.charAt(start));
            start++;
        }
        return sb.length() > 0 ? Integer.parseInt(sb.toString()) : 0;
    }

    private String extractStringValue(String json, String startKey, String endKey) {
        int startIndex = json.indexOf(startKey);
        if (startIndex == -1) return "unknown";
        startIndex += startKey.length();
        int endIndex = json.indexOf(endKey, startIndex);
        if (endIndex == -1) return "unknown";
        return json.substring(startIndex, endIndex);
    }

    /**
     * Transforms live control status into service health statuses for the dashboard.
     */
    private List<ServiceHealthStatus> transformToServiceHealthStatuses(ControlStatusResponse liveStatus) {
        List<ServiceHealthStatus> statuses = new ArrayList<>();
        Instant now = Instant.now();

        // Transaction counts as services
        ControlStatusResponse.TransactionCounts counts = liveStatus.getTransactionCounts();
        if (counts != null) {
            statuses.add(createServiceStatus("Transactions - SUCCESS", counts.getSUCCESS(), now));
            statuses.add(createServiceStatus("Transactions - FAILED", counts.getFAILED(), now));
            statuses.add(createServiceStatus("Transactions - SUBMITTED", counts.getSUBMITTED(), now));
            statuses.add(createServiceStatus("Transactions - PENDING", counts.getPENDING(), now));
            statuses.add(createServiceStatus("Transactions - UNKNOWN", counts.getUNKNOWN(), now));
            statuses.add(createServiceStatus("Transactions - COMPENSATED", counts.getCOMPENSATED(), now));
        }

        // Infrastructure
        ControlStatusResponse.Infrastructure infra = liveStatus.getInfrastructure();
        if (infra != null) {
            statuses.add(createServiceStatus("Database - " + infra.getDatabase(), 1, now));
            statuses.add(createServiceStatus("Cache - " + infra.getCache(), 1, now));
            statuses.add(createServiceStatus("Messaging - " + infra.getMessaging(), 1, now));
            statuses.add(createServiceStatus("Crypto Enabled", infra.isCryptoEnabled() ? 1 : 0, now));
        }

        // Service sizes
        ControlStatusResponse.ServiceSizes sizes = liveStatus.getServiceSizes();
        if (sizes != null) {
            statuses.add(createServiceStatus("Total Transactions", sizes.getTotalTransactions(), now));
            statuses.add(createServiceStatus("Ledger Entries", sizes.getLedgerEntries(), now));
            statuses.add(createServiceStatus("Idempotency Keys", sizes.getIdempotencyKeys(), now));
        }

        // Failure toggles
        ControlStatusResponse.Toggles toggles = liveStatus.getToggles();
        if (toggles != null) {
            statuses.add(createServiceStatus("NPCI Failure Mode", toggles.isNpciFailureMode() ? 0 : 1, now));
            statuses.add(createServiceStatus("CBS Failure Mode", toggles.isCbsFailureMode() ? 0 : 1, now));
            statuses.add(createServiceStatus("Webhook Suppression", toggles.isNpciWebhookSuppressed() ? 0 : 1, now));
        }

        return statuses;
    }

    private ServiceHealthStatus createServiceStatus(String name, int value, Instant now) {
        String status = value > 0 ? STATUS_UP : (value == 0 ? STATUS_DOWN : STATUS_DEGRADED);
        return new ServiceHealthStatus(
                name,
                status,
                0,
                now,
                200,
                String.valueOf(value)
        );
    }

    /**
     * Determines overall system status from live data.
     */
    private String determineOverallStatus(ControlStatusResponse liveStatus) {
        ControlStatusResponse.Toggles toggles = liveStatus.getToggles();
        if (toggles != null && (toggles.isNpciFailureMode() || toggles.isCbsFailureMode())) {
            return STATUS_DEGRADED;
        }

        ControlStatusResponse.TransactionCounts counts = liveStatus.getTransactionCounts();
        if (counts != null && counts.getFAILED() > 0) {
            return STATUS_DEGRADED;
        }

        return STATUS_UP;
    }

    /**
     * Creates an unavailable response when the live endpoint cannot be reached.
     */
    private OverviewResponse createUnavailableResponse(Exception ex) {
        List<ServiceHealthStatus> services = new ArrayList<>();
        services.add(new ServiceHealthStatus(
                "LIVE SERVER UNAVAILABLE",
                STATUS_DOWN,
                -1,
                Instant.now(),
                null,
                ex.getMessage()
        ));

        return new OverviewResponse(
                Instant.now(),
                services,
                STATUS_DOWN,
                0,
                1,
                0,
                1
        );
    }

    /**
     * Creates an error status when a future completes exceptionally.
     */
    private ServiceHealthStatus createErrorStatus(ServiceConfig config, Throwable ex) {
        String errorMsg = ex.getMessage();
        if (errorMsg == null || errorMsg.isEmpty()) {
            errorMsg = "Health check failed";
        }
        log.error("Health check exceptionally failed for {}: {}", config.getName(), errorMsg);
        return new ServiceHealthStatus(
                config.getName(),
                STATUS_ERROR,
                -1,
                Instant.now(),
                null,
                errorMsg
        );
    }

    /**
     * Shuts down the executor service.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down HealthAggregationService executor");
        executorService.shutdown();
    }
}
