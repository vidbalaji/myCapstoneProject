package org.example.paymentservice_aug2026.paymentgateways;

import org.example.paymentservice_aug2026.models.PaymentGatewayType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Stands in for a real gateway in local runs, demos and automated tests. Its link opens a page served by
// MockPaymentController, which simulates the gateway calling us back with success or failure.
@Component
public class MockPaymentGateway implements IPaymentGateway {

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Override
    public PaymentGatewayType getType() {
        return PaymentGatewayType.MOCK;
    }

    @Override
    public PaymentLinkResult createPaymentLink(Long amount, String currency, String phoneNumber, String name,
                                               String email, String orderId) {
        return new PaymentLinkResult(publicBaseUrl + "/payment/mock/" + orderId,
                "mock_" + UUID.randomUUID());
    }
}
