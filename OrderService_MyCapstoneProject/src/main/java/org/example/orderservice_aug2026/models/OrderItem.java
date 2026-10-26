package org.example.orderservice_aug2026.models;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

// A snapshot of the product at purchase time: later price or name changes must not alter past orders
@Setter
@Getter
@Entity
public class OrderItem extends BaseModel {
    @ManyToOne
    private Order order;

    private Long productId;

    private String productName;

    private Double unitPrice;

    private Integer quantity;
}
