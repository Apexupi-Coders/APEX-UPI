package com.apexupi.psp_switch.service;

import com.apexupi.psp_switch.exception.IdempotencyMismatchException;
import com.apexupi.psp_switch.model.PaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Component
public class IdempotencyService {

    private static final String PREFIX = "idemp:";
    private static final String VALUE_SEPARATOR = "|";
    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public IdempotencyService(RedisTemplate<String, String> redisTemplate,
                              @Value("${app.idempotency.ttl:PT24H}") String ttlStr) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.parse(ttlStr);
    }

    /**
     * Compute deterministic request hash from payer, payee, amount
     */
    public String computeRequestHash(PaymentRequest request) {
        String input = request.getPayer() + "|" + request.getPayee() + "|" + request.getAmount();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes).substring(0, 16); // First 16 chars
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Atomic production-grade idempotency check + set
     * @param request PaymentRequest for hash computation
     * @param requestId idempotency key
     * @return Optional.empty() if new (proceed), or IdempotencyResult if duplicate
     * @throws IdempotencyMismatchException if hash mismatch
     */
    public Optional<IdempotencyResult> checkAndSet(PaymentRequest request, String requestId) {
        String key = PREFIX + requestId;
        String reqHash = computeRequestHash(request);

        // Atomic check if new
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "NEW|" + reqHash + "|PENDING", ttl);
        if (Boolean.TRUE.equals(isNew)) {
            log.info("✅ NEW idempotent request registered: key={}, hash={}", requestId, reqHash);
            return Optional.empty();
        }

        // Exists: validate hash
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            return Optional.empty(); // Race, treat as new
        }

        String[] parts = stored.split("\\" + VALUE_SEPARATOR, 3);
        if (parts.length != 3) {
            log.warn("Invalid idempotency format: {}", stored);
            return Optional.empty();
        }

        String storedHash = parts[1];
        if (!reqHash.equals(storedHash)) {
            log.error("🚨 IDEMPOTENCY MISMATCH: key={}, expectedHash={}, gotHash={}", requestId, storedHash, reqHash);
            throw new IdempotencyMismatchException("Request content mismatch for idempotency key: " + requestId);
        }

        log.info("🔄 Duplicate request (hash validated): key={}, txnId={}, status={}", requestId, parts[0], parts[2]);
        return Optional.of(new IdempotencyResult(parts[0], parts[2]));
    }

    /**
     * Update status after processing (txnId now known)
     */
    public void updateStatus(String requestId, String txnId, String status) {
        String key = PREFIX + requestId;
        // Get current hash (assume first set has it)
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null) {
            String[] parts = stored.split("\\" + VALUE_SEPARATOR, 3);
            if (parts.length == 3) {
                String newValue = txnId + VALUE_SEPARATOR + parts[1] + VALUE_SEPARATOR + status;
                redisTemplate.opsForValue().set(key, newValue, ttl);
                log.info("📝 Idempotency status updated: key={}, txnId={}, status={}", requestId, txnId, status);
                return;
            }
        }
        // Fallback set
        redisTemplate.opsForValue().set(key, txnId + VALUE_SEPARATOR + "unknown" + VALUE_SEPARATOR + status, ttl);
        log.warn("⚠️ Fallback idempotency update: key={}, txnId={}, status={}", requestId, txnId, status);
    }
}

