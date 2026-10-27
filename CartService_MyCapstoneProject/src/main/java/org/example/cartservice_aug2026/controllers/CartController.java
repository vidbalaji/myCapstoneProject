package org.example.cartservice_aug2026.controllers;

import org.example.cartservice_aug2026.dtos.AddToCartRequestDto;
import org.example.cartservice_aug2026.dtos.CartDto;
import org.example.cartservice_aug2026.dtos.CartItemDto;
import org.example.cartservice_aug2026.dtos.UpdateQuantityRequestDto;
import org.example.cartservice_aug2026.models.Cart;
import org.example.cartservice_aug2026.models.CartItem;
import org.example.cartservice_aug2026.services.ICartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

// X-User-Id is added by the API Gateway after validating the caller's token,
// so a user can only ever see and change their own cart.
@RestController
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private ICartService cartService;

    @GetMapping
    public CartDto getCart(@RequestHeader("X-User-Id") Long userId) {
        return from(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(@RequestHeader("X-User-Id") Long userId,
                                           @RequestBody AddToCartRequestDto addToCartRequestDto) {
        Integer quantity = addToCartRequestDto.getQuantity() == null ? 1 : addToCartRequestDto.getQuantity();
        Cart cart = cartService.addItem(userId, addToCartRequestDto.getProductId(), quantity);
        return new ResponseEntity<>(from(cart), HttpStatus.OK);
    }

    @PutMapping("/items/{productId}")
    public CartDto updateQuantity(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable Long productId,
                                  @RequestBody UpdateQuantityRequestDto updateQuantityRequestDto) {
        return from(cartService.updateItemQuantity(userId, productId, updateQuantityRequestDto.getQuantity()));
    }

    @DeleteMapping("/items/{productId}")
    public CartDto removeItem(@RequestHeader("X-User-Id") Long userId, @PathVariable Long productId) {
        return from(cartService.removeItem(userId, productId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@RequestHeader("X-User-Id") Long userId) {
        cartService.clearCart(userId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private CartDto from(Cart cart) {
        CartDto cartDto = new CartDto();
        cartDto.setUserId(cart.getUserId());
        List<CartItemDto> items = new ArrayList<>();
        int totalItems = 0;
        double totalAmount = 0;
        for (CartItem item : cart.getItems()) {
            CartItemDto itemDto = new CartItemDto();
            itemDto.setProductId(item.getProductId());
            itemDto.setProductName(item.getProductName());
            itemDto.setImageUrl(item.getImageUrl());
            itemDto.setUnitPrice(item.getUnitPrice());
            itemDto.setQuantity(item.getQuantity());
            itemDto.setLineTotal(item.getUnitPrice() * item.getQuantity());
            items.add(itemDto);
            totalItems += item.getQuantity();
            totalAmount += itemDto.getLineTotal();
        }
        cartDto.setItems(items);
        cartDto.setTotalItems(totalItems);
        // round to paise
        cartDto.setTotalAmount(Math.round(totalAmount * 100) / 100.0);
        return cartDto;
    }
}
