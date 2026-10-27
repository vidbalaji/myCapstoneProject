package org.example.paymentservice_aug2026.paymentgateways;

import org.example.paymentservice_aug2026.models.PaymentGatewayType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Strategy pattern: the active gateway is chosen by configuration (payment.gateway) instead of @Primary,
// so switching from Stripe to Razorpay is a config change, not a code change.
@Component
public class PaymentGatewaySelector {

    private final Map<PaymentGatewayType, IPaymentGateway> gateways = new EnumMap<>(PaymentGatewayType.class);

    private final PaymentGatewayType activeGateway;

    public PaymentGatewaySelector(List<IPaymentGateway> paymentGateways,
                                  @Value("${payment.gateway}") PaymentGatewayType activeGateway) {
        for (IPaymentGateway gateway : paymentGateways) {
            gateways.put(gateway.getType(), gateway);
        }
        this.activeGateway = activeGateway;
    }

    public IPaymentGateway getActiveGateway() {
        return gateways.get(activeGateway);
    }
}
