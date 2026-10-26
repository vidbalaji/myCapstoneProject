package org.example.paymentservice_aug2026.controllers;

import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.example.paymentservice_aug2026.exceptions.WebhookVerificationException;
import org.example.paymentservice_aug2026.models.Payment;
import org.example.paymentservice_aug2026.services.PaymentService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment/webhook/razorpay")
public class RazorpayWebhookController {

    @Autowired
    private PaymentService paymentService;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleRazorpayEvent(@RequestBody String payload,
                                                      @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new WebhookVerificationException("Razorpay webhook secret is not configured");
        }
        try {
            if (signature == null || !Utils.verifyWebhookSignature(payload, signature, webhookSecret)) {
                throw new WebhookVerificationException("Invalid Razorpay signature");
            }
        } catch (RazorpayException exception) {
            throw new WebhookVerificationException("Invalid Razorpay signature");
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.getString("event");
        JSONObject body = event.getJSONObject("payload");
        if (!body.has("payment_link")) {
            return ResponseEntity.ok("ignored");
        }
        String paymentLinkId = body.getJSONObject("payment_link").getJSONObject("entity").getString("id");
        Payment payment = paymentService.getByGatewayReference(paymentLinkId);
        switch (eventType) {
            case "payment_link.paid" -> {
                String gatewayPaymentId = body.has("payment")
                        ? body.getJSONObject("payment").getJSONObject("entity").getString("id") : null;
                paymentService.markSucceeded(payment, gatewayPaymentId);
            }
            case "payment_link.expired", "payment_link.cancelled" ->
                    paymentService.markFailed(payment, "Razorpay: " + eventType);
            default -> { }
        }
        return ResponseEntity.ok("processed");
    }
}
