package org.example.paymentservice_aug2026.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paymentservice_aug2026.clients.KafkaProducerClient;
import org.example.paymentservice_aug2026.dtos.OrderEventDto;
import org.example.paymentservice_aug2026.dtos.PaymentEventDto;
import org.example.paymentservice_aug2026.exceptions.PaymentNotFoundException;
import org.example.paymentservice_aug2026.models.Payment;
import org.example.paymentservice_aug2026.models.PaymentStatus;
import org.example.paymentservice_aug2026.paymentgateways.IPaymentGateway;
import org.example.paymentservice_aug2026.paymentgateways.PaymentGatewaySelector;
import org.example.paymentservice_aug2026.paymentgateways.PaymentLinkResult;
import org.example.paymentservice_aug2026.repos.PaymentRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

@Service
public class PaymentService {

    public static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private PaymentGatewaySelector paymentGatewaySelector;

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${payment.currency}")
    private String currency;

    // Original synchronous API (POST /payment): create a link for an arbitrary amount
    public String getPaymentLink(Long amount, String phoneNumber, String name, String email, String orderId) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0 (in paise)");
        }
        Long orderIdValue = Long.valueOf(orderId);
        Optional<Payment> existing = paymentRepo.findByOrderId(orderIdValue);
        if (existing.isPresent()) {
            return existing.get().getPaymentLink();
        }
        Payment payment = createPayment(orderIdValue, null, email, amount, phoneNumber, name);
        return payment.getPaymentLink();
    }

    // Consumes ORDER_PLACED. Kafka may redeliver a message, so an order that already has a payment is skipped.
    @Transactional
    public Payment createPaymentForOrder(OrderEventDto orderEvent) {
        Optional<Payment> existing = paymentRepo.findByOrderId(orderEvent.getOrderId());
        if (existing.isPresent()) {
            log.info("Payment for order {} already exists, ignoring duplicate event", orderEvent.getOrderId());
            return existing.get();
        }
        long amountInPaise = Math.round(orderEvent.getTotalAmount() * 100);
        return createPayment(orderEvent.getOrderId(), orderEvent.getUserId(), orderEvent.getUserEmail(),
                amountInPaise, null, orderEvent.getUserEmail());
    }

    private Payment createPayment(Long orderId, Long userId, String email, Long amount, String phoneNumber, String name) {
        IPaymentGateway gateway = paymentGatewaySelector.getActiveGateway();
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setUserEmail(email);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setGateway(gateway.getType());
        try {
            PaymentLinkResult link = gateway.createPaymentLink(amount, currency, phoneNumber, name, email,
                    String.valueOf(orderId));
            payment.setPaymentLink(link.url());
            payment.setGatewayReference(link.reference());
            payment.setStatus(PaymentStatus.PENDING);
        } catch (RuntimeException exception) {
            log.error("Could not create {} payment link for order {}: {}", gateway.getType(), orderId, exception.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Could not create payment link");
        }
        Payment saved = paymentRepo.save(payment);
        publish(saved.getStatus() == PaymentStatus.PENDING ? "PAYMENT_LINK_CREATED" : "PAYMENT_FAILED", saved);
        return saved;
    }

    // Called from verified gateway webhooks (or the mock gateway). Idempotent: gateways retry webhooks.
    @Transactional
    public Payment markSucceeded(Payment payment, String gatewayPaymentId) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return payment;
        }
        Date now = new Date();
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setPaidAt(now);
        payment.setLastUpdatedAt(now);
        payment.setReceiptNumber("RCPT-" + new SimpleDateFormat("yyyyMMdd").format(now) + "-" + payment.getId());
        Payment saved = paymentRepo.save(payment);
        publish("PAYMENT_SUCCEEDED", saved);
        return saved;
    }

    @Transactional
    public Payment markFailed(Payment payment, String reason) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment;
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setLastUpdatedAt(new Date());
        Payment saved = paymentRepo.save(payment);
        publish("PAYMENT_FAILED", saved);
        return saved;
    }

    @Transactional
    public void handleOrderCancelled(Long orderId) {
        paymentRepo.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.FAILED) {
                payment.setStatus(PaymentStatus.CANCELLED);
            } else if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                // money was taken: flag it so the reconciliation job refunds it
                payment.setStatus(PaymentStatus.REFUND_PENDING);
            }
            payment.setLastUpdatedAt(new Date());
            paymentRepo.save(payment);
        });
    }

    public Payment getByGatewayReference(String gatewayReference) {
        return paymentRepo.findByGatewayReference(gatewayReference)
                .orElseThrow(() -> new PaymentNotFoundException("No payment for gateway reference " + gatewayReference));
    }

    public Payment getByOrderId(Long orderId) {
        return paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment for order " + orderId));
    }

    // Users may only read their own payments
    public Payment getByOrderIdForUser(Long orderId, Long userId) {
        Payment payment = getByOrderId(orderId);
        if (payment.getUserId() != null && !payment.getUserId().equals(userId)) {
            throw new PaymentNotFoundException("No payment for order " + orderId);
        }
        return payment;
    }

    private void publish(String eventType, Payment payment) {
        PaymentEventDto event = new PaymentEventDto();
        event.setEventType(eventType);
        event.setOrderId(payment.getOrderId());
        event.setPaymentId(payment.getId());
        event.setUserId(payment.getUserId());
        event.setUserEmail(payment.getUserEmail());
        event.setStatus(payment.getStatus().name());
        event.setPaymentLink(payment.getPaymentLink());
        event.setAmount(payment.getAmount());
        event.setCurrency(payment.getCurrency());
        event.setGateway(payment.getGateway().name());
        event.setReceiptNumber(payment.getReceiptNumber());
        event.setFailureReason(payment.getFailureReason());
        event.setTimestamp(System.currentTimeMillis());
        try {
            kafkaProducerClient.sendMessage(PAYMENT_EVENTS_TOPIC, String.valueOf(payment.getOrderId()),
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }
}
