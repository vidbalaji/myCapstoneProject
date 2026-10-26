package org.example.orderservice_aug2026.configs;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced //client side LB through Eureka
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
