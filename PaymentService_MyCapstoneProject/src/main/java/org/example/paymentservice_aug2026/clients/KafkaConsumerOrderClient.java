package org.example.paymentservice_aug2026.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paymentservice_aug2026.dtos.OrderEventDto;
import org.example.paymentservice_aug2026.services.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// HLD flow: "After placing the order, a message is sent to Kafka. Payment Service consumes it to process payment."
@Component
public class KafkaConsumerOrderClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentService paymentService;

    @KafkaListener(topics = "order-events", groupId = "paymentservice")
    public void onOrderEvent(String message) {
        try {
            OrderEventDto orderEvent = objectMapper.readValue(message, OrderEventDto.class);
            switch (orderEvent.getEventType()) {
                case "ORDER_PLACED" -> paymentService.createPaymentForOrder(orderEvent);
                case "ORDER_CANCELLED" -> paymentService.handleOrderCancelled(orderEvent.getOrderId());
                default -> { }
            }
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }
}
