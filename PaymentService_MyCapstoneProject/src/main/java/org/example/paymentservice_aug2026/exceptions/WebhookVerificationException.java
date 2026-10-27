package org.example.paymentservice_aug2026.exceptions;

public class WebhookVerificationException extends RuntimeException {
    public WebhookVerificationException(String message) {
        super(message);
    }
}
