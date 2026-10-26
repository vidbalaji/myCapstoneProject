package org.example.orderservice_aug2026.clients;

import org.example.orderservice_aug2026.dtos.ProductDto;
import org.example.orderservice_aug2026.exceptions.DownstreamServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ProductServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    public ProductDto getProduct(Long productId) {
        try {
            return restTemplate.getForObject("http://productservice/products/{id}", ProductDto.class, productId);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new IllegalArgumentException("Product " + productId + " is no longer available; remove it from the cart");
        } catch (RestClientException exception) {
            throw new DownstreamServiceException("Product service unavailable: " + exception.getMessage());
        }
    }
}
