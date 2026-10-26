package org.example.paymentservice_aug2026.controllers;

import org.example.paymentservice_aug2026.exceptions.PaymentNotFoundException;
import org.example.paymentservice_aug2026.exceptions.WebhookVerificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerAdvisor {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<String> handleNotFound(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({IllegalArgumentException.class, WebhookVerificationException.class})
    public ResponseEntity<String> handleBadRequest(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<String> handleMissingUser(Exception exception) {
        return new ResponseEntity<>("Missing X-User-Id: call this API through the API Gateway", HttpStatus.UNAUTHORIZED);
    }
}
