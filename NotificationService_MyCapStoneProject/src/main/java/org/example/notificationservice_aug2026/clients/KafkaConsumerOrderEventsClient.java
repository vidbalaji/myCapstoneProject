package org.example.notificationservice_aug2026.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.notificationservice_aug2026.dtos.OrderEventDto;
import org.example.notificationservice_aug2026.dtos.PaymentEventDto;
import org.example.notificationservice_aug2026.services.EmailSender;
import org.example.notificationservice_aug2026.services.NotificationTemplates;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Order confirmations / status updates (PRD 4.1, 4.3) and payment receipts (PRD 5.3)
@Component
public class KafkaConsumerOrderEventsClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailSender emailSender;

    @KafkaListener(topics = "order-events", groupId = "notificationservice")
    public void onOrderEvent(String message) throws JsonProcessingException {
        OrderEventDto event = objectMapper.readValue(message, OrderEventDto.class);
        NotificationTemplates.Message email = NotificationTemplates.forOrderEvent(event);
        if (email != null) {
            emailSender.send(event.getUserEmail(), email.subject(), email.body());
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "notificationservice")
    public void onPaymentEvent(String message) throws JsonProcessingException {
        PaymentEventDto event = objectMapper.readValue(message, PaymentEventDto.class);
        NotificationTemplates.Message email = NotificationTemplates.forPaymentEvent(event);
        if (email != null) {
            emailSender.send(event.getUserEmail(), email.subject(), email.body());
        }
    }
}
