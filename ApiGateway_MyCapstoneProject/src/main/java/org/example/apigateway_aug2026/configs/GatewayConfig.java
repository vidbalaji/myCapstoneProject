package org.example.apigateway_aug2026.configs;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class GatewayConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    // Created up front so the revocation listener is subscribed before the first logout happens
    @Bean
    public NewTopic sessionRevokedTopic() {
        return TopicBuilder.name("session-revoked").partitions(3).replicas(1).build();
    }

    @Bean
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                // behind the AWS load balancer the original client is the first address
                return Mono.just(forwardedFor.split(",")[0].trim());
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            return Mono.just(remote == null ? "unknown" : remote.getAddress().getHostAddress());
        };
    }
}
