package com.apexupi.operations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration for monitored backend services.
 * 
 * Add or remove services here - no frontend changes required.
 */
@Component
public class ServiceEndpoint {

    private final java.util.List<ServiceConfig> endpoints;

    public ServiceEndpoint() {
        this.endpoints = createDefaultServices();
    }

    private static java.util.List<ServiceConfig> createDefaultServices() {
        java.util.List<ServiceConfig> defaults = new java.util.ArrayList<>();
        
        ServiceConfig psp = new ServiceConfig();
        psp.setName("PSP VM");
        psp.setUrl("http://localhost:8080/actuator/health");
        psp.setExpectedStatus(200);
        psp.setTimeoutMs(3000);
        defaults.add(psp);
        
        ServiceConfig npci = new ServiceConfig();
        npci.setName("NPCI VM");
        npci.setUrl("http://localhost:8082/actuator/health");
        npci.setExpectedStatus(200);
        npci.setTimeoutMs(3000);
        defaults.add(npci);
        
        ServiceConfig cbs = new ServiceConfig();
        cbs.setName("CBS VM");
        cbs.setUrl("http://localhost:8083/actuator/health");
        cbs.setExpectedStatus(200);
        cbs.setTimeoutMs(3000);
        defaults.add(cbs);
        
        ServiceConfig bankingSwitch = new ServiceConfig();
        bankingSwitch.setName("Banking Switch VM");
        bankingSwitch.setUrl("http://localhost:8084/actuator/health");
        bankingSwitch.setExpectedStatus(200);
        bankingSwitch.setTimeoutMs(3000);
        defaults.add(bankingSwitch);
        
        ServiceConfig kafka = new ServiceConfig();
        kafka.setName("Kafka");
        kafka.setUrl("http://localhost:9092");
        kafka.setExpectedStatus(200);
        kafka.setTimeoutMs(3000);
        defaults.add(kafka);
        
        ServiceConfig redis = new ServiceConfig();
        redis.setName("Redis");
        redis.setUrl("http://localhost:6379");
        redis.setExpectedStatus(200);
        redis.setTimeoutMs(3000);
        defaults.add(redis);
        
        ServiceConfig postgres = new ServiceConfig();
        postgres.setName("PostgreSQL");
        postgres.setUrl("http://localhost:5432");
        postgres.setExpectedStatus(200);
        postgres.setTimeoutMs(3000);
        defaults.add(postgres);
        
        ServiceConfig orchestrator = new ServiceConfig();
        orchestrator.setName("Transaction Orchestrator");
        orchestrator.setUrl("http://localhost:8085/actuator/health");
        orchestrator.setExpectedStatus(200);
        orchestrator.setTimeoutMs(3000);
        defaults.add(orchestrator);
        
        ServiceConfig audit = new ServiceConfig();
        audit.setName("Audit Service");
        audit.setUrl("http://localhost:8086/actuator/health");
        audit.setExpectedStatus(200);
        audit.setTimeoutMs(3000);
        defaults.add(audit);
        
        return defaults;
    }

    public java.util.List<ServiceConfig> getEndpoints() {
        return endpoints;
    }

    public static class ServiceConfig {
        /**
         * Unique service identifier (e.g., "PSP VM", "NPCI VM", "Kafka")
         */
        private String name;

        /**
         * Health check URL (e.g., http://localhost:8080/actuator/health)
         */
        private String url;

        /**
         * HTTP method to use (default: GET)
         */
        private String method = "GET";

        /**
         * Expected HTTP status code for healthy service (default: 200)
         */
        private int expectedStatus = 200;

        /**
         * Timeout in milliseconds (default: 3000)
         */
        private int timeoutMs = 3000;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public int getExpectedStatus() {
            return expectedStatus;
        }

        public void setExpectedStatus(int expectedStatus) {
            this.expectedStatus = expectedStatus;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}