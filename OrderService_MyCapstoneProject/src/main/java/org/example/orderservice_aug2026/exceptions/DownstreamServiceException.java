package org.example.orderservice_aug2026.exceptions;

public class DownstreamServiceException extends RuntimeException {
    public DownstreamServiceException(String message) {
        super(message);
    }
}
