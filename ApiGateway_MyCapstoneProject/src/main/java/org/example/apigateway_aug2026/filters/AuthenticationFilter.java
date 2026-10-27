package org.example.apigateway_aug2026.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.apigateway_aug2026.dtos.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

// Authenticates every request once, at the edge. Downstream services trust the X-User-* headers this
// filter sets, so any copy of those headers sent by a client is removed first.
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String SESSION_COOKIE = "auth_session_id";
    static final String CACHE_PREFIX = "auth:token:";

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final RouteAccessPolicy routeAccessPolicy;
    private final WebClient userServiceClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public AuthenticationFilter(RouteAccessPolicy routeAccessPolicy,
                                WebClient.Builder loadBalancedWebClientBuilder,
                                ReactiveStringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${gateway.auth.cache-ttl-seconds}") long cacheTtlSeconds) {
        this.routeAccessPolicy = routeAccessPolicy;
        this.userServiceClient = loadBalancedWebClientBuilder.baseUrl("http://userservice").build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                })
                .build();
        ServerWebExchange cleaned = exchange.mutate().request(request).build();

        String path = request.getPath().value();
        RouteAccessPolicy.Access access = routeAccessPolicy.requiredAccess(request.getMethod(), path);
        String token = extractToken(request);

        if (access == RouteAccessPolicy.Access.PUBLIC) {
            if (path.equals("/auth/logout") && token != null) {
                // forget the cached validation so the token stops working immediately
                return evict(token).then(chain.filter(cleaned));
            }
            return chain.filter(cleaned);
        }
        if (token == null) {
            return reject(cleaned, HttpStatus.UNAUTHORIZED);
        }

        return resolveUser(token)
                .flatMap(user -> {
                    if (access == RouteAccessPolicy.Access.ADMIN
                            && (user.getRoles() == null || !user.getRoles().contains("ADMIN"))) {
                        return reject(cleaned, HttpStatus.FORBIDDEN);
                    }
                    ServerHttpRequest withUser = cleaned.getRequest().mutate()
                            .header(USER_ID_HEADER, String.valueOf(user.getId()))
                            .header(USER_EMAIL_HEADER, user.getEmail() == null ? "" : user.getEmail())
                            .header(USER_ROLES_HEADER, user.getRoles() == null ? "" : String.join(",", user.getRoles()))
                            .build();
                    return chain.filter(cleaned.mutate().request(withUser).build());
                })
                .switchIfEmpty(Mono.defer(() -> reject(cleaned, HttpStatus.UNAUTHORIZED)))
                .onErrorResume(UserServiceUnavailableException.class,
                        exception -> reject(cleaned, HttpStatus.SERVICE_UNAVAILABLE));
    }

    // Empty Mono = token invalid
    private Mono<UserDto> resolveUser(String token) {
        if (cacheTtl.isZero()) {
            return validateWithUserService(token);
        }
        String key = CACHE_PREFIX + sha256(token);
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> Mono.justOrEmpty(fromJson(json)))
                .onErrorResume(exception -> {
                    log.warn("Auth cache unavailable, validating directly: {}", exception.getMessage());
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> validateWithUserService(token)
                        .flatMap(user -> redisTemplate.opsForValue().set(key, toJson(user), cacheTtl)
                                .onErrorResume(exception -> Mono.just(false))
                                .thenReturn(user))));
    }

    private Mono<UserDto> validateWithUserService(String token) {
        return userServiceClient.post()
                .uri("/auth/validate")
                .bodyValue(Map.of("token", token))
                .retrieve()
                .bodyToMono(UserDto.class)
                .onErrorResume(WebClientResponseException.class, exception -> {
                    if (exception.getStatusCode().is4xxClientError()) {
                        return Mono.empty();
                    }
                    return Mono.error(new UserServiceUnavailableException());
                })
                .onErrorResume(exception -> !(exception instanceof UserServiceUnavailableException),
                        exception -> Mono.error(new UserServiceUnavailableException()));
    }

    private Mono<Boolean> evict(String token) {
        return redisTemplate.delete(CACHE_PREFIX + sha256(token))
                .map(count -> count > 0)
                .onErrorResume(exception -> Mono.just(false));
    }

    static String extractToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        HttpCookie cookie = request.getCookies().getFirst(SESSION_COOKIE);
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        return null;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    private UserDto fromJson(String json) {
        try {
            return objectMapper.readValue(json, UserDto.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String toJson(UserDto user) {
        try {
            return objectMapper.writeValueAsString(user);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // Tokens are not stored in Redis in clear text
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    static class UserServiceUnavailableException extends RuntimeException {
    }
}
