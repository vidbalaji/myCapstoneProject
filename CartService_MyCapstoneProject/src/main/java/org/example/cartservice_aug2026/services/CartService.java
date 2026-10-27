package org.example.cartservice_aug2026.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cartservice_aug2026.clients.KafkaProducerClient;
import org.example.cartservice_aug2026.clients.ProductServiceClient;
import org.example.cartservice_aug2026.configs.CartConfig;
import org.example.cartservice_aug2026.dtos.CartEventDto;
import org.example.cartservice_aug2026.dtos.ProductDto;
import org.example.cartservice_aug2026.exceptions.CartItemNotFoundException;
import org.example.cartservice_aug2026.models.Cart;
import org.example.cartservice_aug2026.models.CartItem;
import org.example.cartservice_aug2026.repos.CartRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
public class CartService implements ICartService {

    public static final String CART_EVENTS_TOPIC = "cart-events";

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private ProductServiceClient productServiceClient;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.cart.max-quantity-per-item}")
    private Integer maxQuantityPerItem;

    // Cache-aside read: Redis first, MongoDB on a miss (the result is then put into Redis)
    @Override
    @Cacheable(value = CartConfig.CART_CACHE, key = "#userId")
    public Cart getCart(Long userId) {
        return loadCart(userId);
    }

    // Write-through: every change is saved to MongoDB and the fresh cart replaces the cached one
    @Override
    @CachePut(value = CartConfig.CART_CACHE, key = "#userId")
    public Cart addItem(Long userId, Long productId, Integer quantity) {
        validateQuantity(quantity);
        ProductDto product = productServiceClient.getProduct(productId);

        Cart cart = loadCart(userId);
        Optional<CartItem> existing = findItem(cart, productId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQuantity = item.getQuantity() + quantity;
            validateQuantity(newQuantity);
            item.setQuantity(newQuantity);
            item.setUnitPrice(product.getPrice());
        } else {
            CartItem item = new CartItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setImageUrl(product.getImageUrl());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(quantity);
            cart.getItems().add(item);
        }
        Cart saved = save(cart);
        publishEvent("ITEM_ADDED", userId, productId, quantity);
        return saved;
    }

    @Override
    @CachePut(value = CartConfig.CART_CACHE, key = "#userId")
    public Cart updateItemQuantity(Long userId, Long productId, Integer quantity) {
        validateQuantity(quantity);
        Cart cart = loadCart(userId);
        CartItem item = findItem(cart, productId)
                .orElseThrow(() -> new CartItemNotFoundException("Product " + productId + " is not in the cart"));
        item.setQuantity(quantity);
        Cart saved = save(cart);
        publishEvent("ITEM_QUANTITY_UPDATED", userId, productId, quantity);
        return saved;
    }

    @Override
    @CachePut(value = CartConfig.CART_CACHE, key = "#userId")
    public Cart removeItem(Long userId, Long productId) {
        Cart cart = loadCart(userId);
        boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        if (!removed) {
            throw new CartItemNotFoundException("Product " + productId + " is not in the cart");
        }
        Cart saved = save(cart);
        publishEvent("ITEM_REMOVED", userId, productId, 0);
        return saved;
    }

    @Override
    @CacheEvict(value = CartConfig.CART_CACHE, key = "#userId")
    public void clearCart(Long userId) {
        cartRepo.deleteById(userId);
        publishEvent("CART_CLEARED", userId, null, 0);
    }

    private Cart loadCart(Long userId) {
        return cartRepo.findById(userId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUserId(userId);
            return cart;
        });
    }

    private Cart save(Cart cart) {
        cart.setUpdatedAt(new Date());
        return cartRepo.save(cart);
    }

    private Optional<CartItem> findItem(Cart cart, Long productId) {
        return cart.getItems().stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > maxQuantityPerItem) {
            throw new IllegalArgumentException("Quantity must be between 1 and " + maxQuantityPerItem);
        }
    }

    // Cart activity is published for analytics/recommendations; failing to publish must not fail the cart update
    private void publishEvent(String eventType, Long userId, Long productId, Integer quantity) {
        CartEventDto event = new CartEventDto();
        event.setEventType(eventType);
        event.setUserId(userId);
        event.setProductId(productId);
        event.setQuantity(quantity);
        event.setTimestamp(System.currentTimeMillis());
        try {
            kafkaProducerClient.sendMessage(CART_EVENTS_TOPIC, String.valueOf(userId), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Could not publish {} event for user {}: {}", eventType, userId, exception.getMessage());
        }
    }
}
