package org.example.cartservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AddToCartRequestDto {
    private Long productId;

    private Integer quantity;
}
