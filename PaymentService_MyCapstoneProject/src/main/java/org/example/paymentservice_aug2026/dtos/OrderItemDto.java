package org.example.paymentservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrderItemDto {
    private Long productId;
    private String productName;
    private Double unitPrice;
    private Integer quantity;
}
