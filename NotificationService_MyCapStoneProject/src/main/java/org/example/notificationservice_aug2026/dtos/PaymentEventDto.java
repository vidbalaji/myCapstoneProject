package org.example.notificationservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PaymentEventDto {
    private String eventType;
    private Long orderId;
    private Long paymentId;
    private Long userId;
    private String userEmail;
    private String status;
    private String paymentLink;
    private Long amount;
    private String currency;
    private String gateway;
    private String receiptNumber;
    private String failureReason;
    private Long timestamp;
}
