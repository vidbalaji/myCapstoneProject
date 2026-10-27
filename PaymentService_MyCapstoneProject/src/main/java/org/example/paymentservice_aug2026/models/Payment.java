package org.example.paymentservice_aug2026.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

// Transaction log for one order's payment
@Setter
@Getter
@Entity
@Table(indexes = @Index(name = "idx_payment_gateway_reference", columnList = "gatewayReference"))
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One payment per order: also what makes order-event processing idempotent
    @Column(unique = true)
    private Long orderId;

    private Long userId;

    private String userEmail;

    // In the smallest currency unit (paise), as payment gateways expect
    private Long amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentGatewayType gateway;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(length = 1024)
    private String paymentLink;

    // Gateway's id for the payment link (Stripe plink_..., Razorpay plink_...), used to match webhooks
    private String gatewayReference;

    // Gateway's id for the actual payment once completed
    private String gatewayPaymentId;

    private String receiptNumber;

    private String failureReason;

    private Date createdAt = new Date();

    private Date lastUpdatedAt = new Date();

    private Date paidAt;
}
