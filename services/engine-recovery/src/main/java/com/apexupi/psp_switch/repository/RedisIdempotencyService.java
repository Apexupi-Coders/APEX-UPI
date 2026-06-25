package com.apexupi.psp_switch.repository;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisIdempotencyService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final String IDEMP_KEY_PREFIX = "idemp:";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Autowired
    public RedisIdempotencyService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean exists(String idempotencyKey) {
        String key = IDEMP_KEY_PREFIX + idempotencyKey;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    public void save(String idempotencyKey, String txnId) {
        String key = IDEMP_KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(key, txnId, TTL);
    }

    public String getTxnId(String idempotencyKey) {
        String key = IDEMP_KEY_PREFIX + idempotencyKey;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void delete(String idempotencyKey) {
        String key = IDEMP_KEY_PREFIX + idempotencyKey;
        redisTemplate.delete(key);
    }
}
