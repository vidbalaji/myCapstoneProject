package org.example.userauthservice_aug2026.controllers;

import org.example.userauthservice_aug2026.exceptions.InvalidTokenException;
import org.example.userauthservice_aug2026.exceptions.PasswordMismatchException;
import org.example.userauthservice_aug2026.exceptions.UserAlreadyExistException;
import org.example.userauthservice_aug2026.exceptions.UserNotRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerAdvisor {

    @ExceptionHandler(UserAlreadyExistException.class)
    public ResponseEntity<String> handleUserAlreadyExist(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UserNotRegisteredException.class)
    public ResponseEntity<String> handleUserNotRegistered(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({PasswordMismatchException.class, InvalidTokenException.class})
    public ResponseEntity<String> handleUnauthorized(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(Exception exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }
}
