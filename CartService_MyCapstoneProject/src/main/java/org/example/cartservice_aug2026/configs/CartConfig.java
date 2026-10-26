package org.example.cartservice_aug2026.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class CartConfig {

    public static final String CART_CACHE = "carts";

    @Bean
    @LoadBalanced //client side LB through Eureka
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Store carts in Redis as JSON (readable with redis-cli) with a TTL so idle carts fall out of memory;
    // MongoDB still holds every cart.
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            @Value("${app.cart.cache-ttl-minutes}") long ttlMinutes) {
        return builder -> builder.withCacheConfiguration(CART_CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(ttlMinutes))
                        .disableCachingNullValues()
                        .serializeValuesWith(RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer())));
    }
}
