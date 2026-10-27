package org.example.paymentservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class ReceiptDto {
    private String receiptNumber;
    private Long paymentId;
    private Long orderId;
    private String customerEmail;
    private Long amount;
    private String currency;
    private String gateway;
    private String gatewayPaymentId;
    private Date paidAt;
}
