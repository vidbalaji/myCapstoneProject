package org.example.cartservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CartItemDto {
    private Long productId;

    private String productName;

    private String imageUrl;

    private Double unitPrice;

    private Integer quantity;

    private Double lineTotal;
}
