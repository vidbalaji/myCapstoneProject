package org.example.paymentservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class PaymentDto {
    private Long paymentId;
    private Long orderId;
    private String status;
    private Long amount;
    private String currency;
    private String gateway;
    private String paymentLink;
    private Date createdAt;
    private Date paidAt;
}
