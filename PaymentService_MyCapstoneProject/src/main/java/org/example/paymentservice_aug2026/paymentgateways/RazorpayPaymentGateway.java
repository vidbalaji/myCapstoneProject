package org.example.paymentservice_aug2026.paymentgateways;

import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.example.paymentservice_aug2026.models.PaymentGatewayType;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RazorpayPaymentGateway implements IPaymentGateway {

    private static final long LINK_VALIDITY_SECONDS = 24 * 60 * 60;

    @Autowired
    private RazorpayClient razorpayClient;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Override
    public PaymentGatewayType getType() {
        return PaymentGatewayType.RAZORPAY;
    }

    @Override
    public PaymentLinkResult createPaymentLink(Long amount, String currency, String phoneNumber, String name,
                                               String email, String orderId) {
        try {
            JSONObject paymentLinkRequest = new JSONObject();
            paymentLinkRequest.put("amount", amount);
            paymentLinkRequest.put("currency", currency);
            paymentLinkRequest.put("accept_partial", false);
            // was a fixed timestamp in the past; links now expire 24h after creation
            paymentLinkRequest.put("expire_by", System.currentTimeMillis() / 1000 + LINK_VALIDITY_SECONDS);
            paymentLinkRequest.put("reference_id", orderId);
            paymentLinkRequest.put("description", "Payment for order #" + orderId);
            JSONObject customer = new JSONObject();
            // name and contact were swapped in the earlier version
            customer.put("name", name);
            if (phoneNumber != null) customer.put("contact", phoneNumber);
            customer.put("email", email);
            paymentLinkRequest.put("customer", customer);
            JSONObject notify = new JSONObject();
            notify.put("sms", phoneNumber != null);
            notify.put("email", true);
            paymentLinkRequest.put("notify", notify);
            paymentLinkRequest.put("reminder_enable", true);
            JSONObject notes = new JSONObject();
            notes.put("orderId", orderId);
            paymentLinkRequest.put("notes", notes);
            paymentLinkRequest.put("callback_url", publicBaseUrl + "/orders/" + orderId);
            paymentLinkRequest.put("callback_method", "get");
            PaymentLink payment = razorpayClient.paymentLink.create(paymentLinkRequest);
            return new PaymentLinkResult(payment.get("short_url"), payment.get("id"));
        }catch (RazorpayException razorpayException) {
            throw new RuntimeException(razorpayException.getMessage());
        }
    }
}
