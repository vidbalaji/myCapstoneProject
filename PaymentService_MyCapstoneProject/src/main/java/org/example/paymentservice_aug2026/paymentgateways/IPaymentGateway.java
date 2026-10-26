package org.example.paymentservice_aug2026.paymentgateways;

import org.example.paymentservice_aug2026.models.PaymentGatewayType;

public interface IPaymentGateway {
    PaymentGatewayType getType();

    // amount is in the smallest currency unit (paise / cents)
    PaymentLinkResult createPaymentLink(Long amount, String currency, String phoneNumber, String name, String email, String orderId);
}
