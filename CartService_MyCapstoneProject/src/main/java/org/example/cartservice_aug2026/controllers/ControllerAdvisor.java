package org.example.cartservice_aug2026.controllers;

import org.example.cartservice_aug2026.exceptions.CartItemNotFoundException;
import org.example.cartservice_aug2026.exceptions.ProductNotFoundException;
import org.example.cartservice_aug2026.exceptions.ProductServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerAdvisor {

    @ExceptionHandler({ProductNotFoundException.class, CartItemNotFoundException.class})
    public ResponseEntity<String> handleNotFound(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ProductServiceUnavailableException.class)
    public ResponseEntity<String> handleUnavailable(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<String> handleMissingUser(Exception exception) {
        return new ResponseEntity<>("Missing X-User-Id: call this API through the API Gateway", HttpStatus.UNAUTHORIZED);
    }
}
