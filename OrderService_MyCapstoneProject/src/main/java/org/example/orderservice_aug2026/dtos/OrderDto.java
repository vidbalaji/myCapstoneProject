package org.example.orderservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Date;

@Setter
@Getter
public class OrderDto {
    private Long id;
    private String status;
    private Double totalAmount;
    private String currency;
    private String paymentMethod;
    private String paymentLink;
    private AddressDto deliveryAddress;
    private List<OrderItemDto> items;
    private Date createdAt;
}
