package org.example.apigateway_aug2026.filters;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

// User Service publishes the SHA-256 of every token it revokes (logout, password reset). Dropping the cached
// validation makes the revocation take effect at once instead of when the cache entry expires.
// The cache lives in Redis, so one consumer group is enough for any number of gateway instances.
@Component
public class SessionRevocationListener {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @KafkaListener(topics = "session-revoked", groupId = "apigateway")
    public void onSessionRevoked(String tokenHash) {
        redisTemplate.delete(AuthenticationFilter.CACHE_PREFIX + tokenHash).block(Duration.ofSeconds(5));
    }
}
