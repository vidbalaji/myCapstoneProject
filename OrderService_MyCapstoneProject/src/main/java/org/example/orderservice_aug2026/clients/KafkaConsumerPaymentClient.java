package org.example.orderservice_aug2026.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.orderservice_aug2026.dtos.PaymentEventDto;
import org.example.orderservice_aug2026.services.IOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Payment Service tells us the outcome of a payment; we move the order forward accordingly
@Component
public class KafkaConsumerPaymentClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IOrderService orderService;

    @KafkaListener(topics = "payment-events", groupId = "orderservice")
    public void onPaymentEvent(String message) {
        try {
            PaymentEventDto paymentEvent = objectMapper.readValue(message, PaymentEventDto.class);
            orderService.handlePaymentEvent(paymentEvent);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }
}
