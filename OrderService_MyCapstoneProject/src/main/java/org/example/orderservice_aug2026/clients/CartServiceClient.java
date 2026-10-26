package org.example.orderservice_aug2026.clients;

import org.example.orderservice_aug2026.dtos.CartDto;
import org.example.orderservice_aug2026.exceptions.DownstreamServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CartServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    public CartDto getCart(Long userId) {
        try {
            return restTemplate.exchange("http://cartservice/cart", HttpMethod.GET,
                    new HttpEntity<>(userHeaders(userId)), CartDto.class).getBody();
        } catch (RestClientException exception) {
            throw new DownstreamServiceException("Cart service unavailable: " + exception.getMessage());
        }
    }

    public void clearCart(Long userId) {
        restTemplate.exchange("http://cartservice/cart", HttpMethod.DELETE,
                new HttpEntity<>(userHeaders(userId)), Void.class);
    }

    private HttpHeaders userHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", String.valueOf(userId));
        return headers;
    }
}
