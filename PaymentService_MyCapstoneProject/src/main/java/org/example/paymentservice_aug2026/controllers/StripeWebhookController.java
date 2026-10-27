package org.example.paymentservice_aug2026.controllers;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.example.paymentservice_aug2026.exceptions.WebhookVerificationException;
import org.example.paymentservice_aug2026.services.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Stripe calls this when a checkout started from one of our payment links completes.
@RestController
@RequestMapping("/stripeWebhook")
public class StripeWebhookController {

    @Autowired
    private PaymentService paymentService;

    @Value("${stripe.webhookSecret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleStripeEvent(@RequestBody String payload,
                                                    @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new WebhookVerificationException("Stripe webhook secret is not configured");
        }
        Event event;
        try {
            // Rejects anything not signed by Stripe, so nobody can mark an order paid by calling this URL
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException exception) {
            throw new WebhookVerificationException("Invalid Stripe signature");
        }

        Session session = toSession(event);
        if (session == null || session.getPaymentLink() == null) {
            return ResponseEntity.ok("ignored");
        }
        switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                if ("paid".equals(session.getPaymentStatus())) {
                    paymentService.markSucceeded(paymentService.getByGatewayReference(session.getPaymentLink()),
                            session.getPaymentIntent());
                }
            }
            case "checkout.session.async_payment_failed", "checkout.session.expired" ->
                    paymentService.markFailed(paymentService.getByGatewayReference(session.getPaymentLink()),
                            "Stripe: " + event.getType());
            default -> { }
        }
        return ResponseEntity.ok("processed");
    }

    private Session toSession(Event event) {
        try {
            StripeObject object = event.getDataObjectDeserializer().getObject()
                    .orElse(event.getDataObjectDeserializer().deserializeUnsafe());
            return object instanceof Session session ? session : null;
        } catch (EventDataObjectDeserializationException exception) {
            return null;
        }
    }
}
