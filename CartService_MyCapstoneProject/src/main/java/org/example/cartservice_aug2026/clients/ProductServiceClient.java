package org.example.cartservice_aug2026.clients;

import org.example.cartservice_aug2026.dtos.ProductDto;
import org.example.cartservice_aug2026.exceptions.ProductNotFoundException;
import org.example.cartservice_aug2026.exceptions.ProductServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ProductServiceClient {

    @Autowired
    private RestTemplate restTemplate;

    // "productservice" is resolved through Eureka by the @LoadBalanced RestTemplate
    public ProductDto getProduct(Long productId) {
        try {
            ProductDto productDto = restTemplate.getForObject("http://productservice/products/{id}",
                    ProductDto.class, productId);
            if (productDto == null) {
                throw new ProductNotFoundException("Product " + productId + " not found");
            }
            return productDto;
        } catch (HttpClientErrorException exception) {
            throw new ProductNotFoundException("Product " + productId + " not found");
        } catch (RestClientException | IllegalStateException exception) {
            // connection refused, 5xx, or no instance registered in Eureka
            throw new ProductServiceUnavailableException("Product service unavailable, please retry");
        }
    }
}
