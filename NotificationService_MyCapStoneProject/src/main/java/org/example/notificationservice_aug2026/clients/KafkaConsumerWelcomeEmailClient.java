package org.example.notificationservice_aug2026.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.notificationservice_aug2026.dtos.EmailDto;
import org.example.notificationservice_aug2026.services.EmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Ready-made emails from User Service: welcome on signup, and password reset links
@Component
public class KafkaConsumerWelcomeEmailClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailSender emailSender;

    @KafkaListener(topics = {"signup", "password-reset"}, groupId = "emailService")
    public void sendEmail(String message) {
        try {
            EmailDto emailDto = objectMapper.readValue(message, EmailDto.class);
            emailSender.send(emailDto.getTo(), emailDto.getSubject(), emailDto.getBody());
        } catch(JsonProcessingException exception) {
            throw new RuntimeException(exception.getMessage());
        }

    }
}
