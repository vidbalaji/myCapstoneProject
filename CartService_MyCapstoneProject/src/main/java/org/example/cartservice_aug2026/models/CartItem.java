package org.example.cartservice_aug2026.models;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CartItem {
    private Long productId;
    private String productName;
    private String imageUrl;
    // Price captured when the item was added; Order Service re-checks it at checkout
    private Double unitPrice;
    private Integer quantity;
}
