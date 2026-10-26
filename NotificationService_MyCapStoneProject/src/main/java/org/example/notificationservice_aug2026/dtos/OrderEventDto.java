package org.example.notificationservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class OrderEventDto {
    private String eventType;
    private Long orderId;
    private Long userId;
    private String userEmail;
    private String status;
    private Double totalAmount;
    private String currency;
    private String paymentMethod;
    private List<OrderItemDto> items;
    private Long timestamp;
}
