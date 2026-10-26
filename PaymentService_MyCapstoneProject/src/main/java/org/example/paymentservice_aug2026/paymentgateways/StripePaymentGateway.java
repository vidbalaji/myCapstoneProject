package org.example.paymentservice_aug2026.paymentgateways;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentLink;
import com.stripe.model.Price;
import com.stripe.param.PaymentLinkCreateParams;
import com.stripe.param.PriceCreateParams;
import org.example.paymentservice_aug2026.models.PaymentGatewayType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGateway implements IPaymentGateway {

    @Value("${stripe.apiKey}")
    private String apiKey;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Override
    public PaymentGatewayType getType() {
        return PaymentGatewayType.STRIPE;
    }

    @Override
    public PaymentLinkResult createPaymentLink(Long amount, String currency, String phoneNumber, String name,
                                               String email, String orderId) {
        try {
            Stripe.apiKey = this.apiKey;
            Price price = getPrice(amount, currency, orderId);
            PaymentLinkCreateParams params =
                    PaymentLinkCreateParams.builder()
                            .addLineItem(
                                    PaymentLinkCreateParams.LineItem.builder()
                                            .setPrice(price.getId())
                                            .setQuantity(1L)
                                            .build()
                            )
                            // lets the webhook map a completed checkout back to our order
                            .putMetadata("orderId", orderId)
                            .setAfterCompletion(PaymentLinkCreateParams.AfterCompletion.builder()
                                    .setType(PaymentLinkCreateParams.AfterCompletion.Type.REDIRECT)
                                    .setRedirect(PaymentLinkCreateParams.AfterCompletion.Redirect.builder()
                                            .setUrl(publicBaseUrl + "/orders/" + orderId)
                                            .build())
                                    .build())
                            .build();
            PaymentLink paymentLink = PaymentLink.create(params);
            return new PaymentLinkResult(paymentLink.getUrl(), paymentLink.getId());
        }catch (StripeException exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }

    // A one-time price for this order (the earlier version created a monthly recurring "Gold Plan" price,
    // which would have turned every order into a subscription).
    private Price getPrice(Long amount, String currency, String orderId) {
        try {
            PriceCreateParams params =
                    PriceCreateParams.builder()
                            .setCurrency(currency.toLowerCase())
                            .setUnitAmount(amount)
                            .setProductData(
                                    PriceCreateParams.ProductData.builder().setName("Order #" + orderId).build()
                            )
                            .build();
            return Price.create(params);
        }catch (StripeException exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }
}
