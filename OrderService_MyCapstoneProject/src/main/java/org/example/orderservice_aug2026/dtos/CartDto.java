package org.example.orderservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class CartDto {
    private Long userId;
    private List<CartItemDto> items;
    private Double totalAmount;
}
