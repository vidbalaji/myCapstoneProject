package org.example.paymentservice_aug2026.controllers;

import org.example.paymentservice_aug2026.dtos.PaymentDto;
import org.example.paymentservice_aug2026.models.Payment;
import org.example.paymentservice_aug2026.models.PaymentGatewayType;
import org.example.paymentservice_aug2026.services.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// The "hosted payment page" of the MOCK gateway. Only acts on payments created with the MOCK gateway,
// so it can never settle a real Stripe/Razorpay payment.
@RestController
@RequestMapping("/payment/mock")
public class MockPaymentController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping(value = "/{orderId}", produces = MediaType.TEXT_HTML_VALUE)
    public String paymentPage(@PathVariable Long orderId) {
        Payment payment = mockPayment(orderId);
        String amount = String.format("%.2f", payment.getAmount() / 100.0);
        return """
                <!doctype html><html><head><meta charset="utf-8"><title>Mock payment</title></head>
                <body style="font-family:sans-serif;max-width:420px;margin:40px auto">
                <h2>Mock payment gateway</h2>
                <p>Order #%d &mdash; %s %s &mdash; status <b>%s</b></p>
                <form method="post" action="%d/complete?success=true"><button>Pay now</button></form><br>
                <form method="post" action="%d/complete?success=false"><button>Simulate failure</button></form>
                </body></html>
                """.formatted(orderId, payment.getCurrency(), amount, payment.getStatus(), orderId, orderId);
    }

    @PostMapping("/{orderId}/complete")
    public PaymentDto complete(@PathVariable Long orderId, @RequestParam(defaultValue = "true") boolean success) {
        Payment payment = mockPayment(orderId);
        Payment result = success
                ? paymentService.markSucceeded(payment, "mockpay_" + UUID.randomUUID())
                : paymentService.markFailed(payment, "Declined by mock gateway");
        PaymentDto paymentDto = new PaymentDto();
        paymentDto.setPaymentId(result.getId());
        paymentDto.setOrderId(result.getOrderId());
        paymentDto.setStatus(result.getStatus().name());
        paymentDto.setAmount(result.getAmount());
        paymentDto.setCurrency(result.getCurrency());
        paymentDto.setGateway(result.getGateway().name());
        paymentDto.setPaidAt(result.getPaidAt());
        return paymentDto;
    }

    private Payment mockPayment(Long orderId) {
        Payment payment = paymentService.getByOrderId(orderId);
        if (payment.getGateway() != PaymentGatewayType.MOCK) {
            throw new IllegalArgumentException("Order " + orderId + " is not paid through the mock gateway");
        }
        return payment;
    }
}
