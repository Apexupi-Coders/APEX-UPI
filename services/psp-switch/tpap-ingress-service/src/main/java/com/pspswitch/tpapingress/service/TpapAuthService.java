package com.pspswitch.tpapingress.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Authenticates inbound TPAP requests against the hardcoded registry.
 * v1: single TPAP 'phonepe' registered in application.yml.
 * Uses constant-time comparison to prevent timing attacks.
 */
@Service
public class TpapAuthService {

    private final Map<String, String> registry;

    public TpapAuthService(@Value("${app.tpap.registry.phonepe.api-key}") String phonepeKey) {
        this.registry = Map.of("phonepe", phonepeKey);
    }

    /**
     * Validates that the tpapId is registered AND the apiKey matches.
     * Uses MessageDigest.isEqual() for constant-time comparison to prevent timing attacks.
     *
     * @param tpapId the TPAP identifier from X-TPAP-ID header
     * @param apiKey the API key from X-TPAP-API-Key header
     * @return true if authentication succeeds
     */
    public boolean authenticate(String tpapId, String apiKey) {
        if (tpapId == null || apiKey == null) {
            return false;
        }
        String expectedKey = registry.get(tpapId.toLowerCase());
        if (expectedKey == null) {
            return false;
        }
        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8));
    }
}
