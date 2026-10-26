package org.example.paymentservice_aug2026.controllers;

import org.example.paymentservice_aug2026.dtos.InitiatePaymentDto;
import org.example.paymentservice_aug2026.dtos.PaymentDto;
import org.example.paymentservice_aug2026.dtos.ReceiptDto;
import org.example.paymentservice_aug2026.models.Payment;
import org.example.paymentservice_aug2026.models.PaymentStatus;
import org.example.paymentservice_aug2026.services.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping
    public String initiatePayment(@RequestBody InitiatePaymentDto initiatePaymentDto) {
        return paymentService.getPaymentLink(initiatePaymentDto.getAmount(),
                initiatePaymentDto.getPhoneNumber(),
                initiatePaymentDto.getName(),
                initiatePaymentDto.getEmail(),
                initiatePaymentDto.getOrderId());
    }

    @GetMapping("/order/{orderId}")
    public PaymentDto getPaymentForOrder(@RequestHeader("X-User-Id") Long userId, @PathVariable Long orderId) {
        return from(paymentService.getByOrderIdForUser(orderId, userId));
    }

    // Payment receipt (PRD 5.3)
    @GetMapping("/order/{orderId}/receipt")
    public ResponseEntity<ReceiptDto> getReceipt(@RequestHeader("X-User-Id") Long userId, @PathVariable Long orderId) {
        Payment payment = paymentService.getByOrderIdForUser(orderId, userId);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        ReceiptDto receiptDto = new ReceiptDto();
        receiptDto.setReceiptNumber(payment.getReceiptNumber());
        receiptDto.setPaymentId(payment.getId());
        receiptDto.setOrderId(payment.getOrderId());
        receiptDto.setCustomerEmail(payment.getUserEmail());
        receiptDto.setAmount(payment.getAmount());
        receiptDto.setCurrency(payment.getCurrency());
        receiptDto.setGateway(payment.getGateway().name());
        receiptDto.setGatewayPaymentId(payment.getGatewayPaymentId());
        receiptDto.setPaidAt(payment.getPaidAt());
        return new ResponseEntity<>(receiptDto, HttpStatus.OK);
    }

    private PaymentDto from(Payment payment) {
        PaymentDto paymentDto = new PaymentDto();
        paymentDto.setPaymentId(payment.getId());
        paymentDto.setOrderId(payment.getOrderId());
        paymentDto.setStatus(payment.getStatus().name());
        paymentDto.setAmount(payment.getAmount());
        paymentDto.setCurrency(payment.getCurrency());
        paymentDto.setGateway(payment.getGateway().name());
        paymentDto.setPaymentLink(payment.getPaymentLink());
        paymentDto.setCreatedAt(payment.getCreatedAt());
        paymentDto.setPaidAt(payment.getPaidAt());
        return paymentDto;
    }
}
