package org.example.cartservice_aug2026.services;

import org.example.cartservice_aug2026.models.Cart;

public interface ICartService {
    Cart getCart(Long userId);

    Cart addItem(Long userId, Long productId, Integer quantity);

    Cart updateItemQuantity(Long userId, Long productId, Integer quantity);

    Cart removeItem(Long userId, Long productId);

    void clearCart(Long userId);
}
