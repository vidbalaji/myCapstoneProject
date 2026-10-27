package org.example.cartservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CartEventDto {
    private String eventType;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private Long timestamp;
}
