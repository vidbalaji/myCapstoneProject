package org.example.orderservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class PaymentEventDto {
    private String eventType;
    private Long orderId;
    private Long paymentId;
    private String status;
    private String paymentLink;
    private Long amount;
    private String gateway;
}
