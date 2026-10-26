package org.example.paymentservice_aug2026.models;

public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    // Paid, but the order was cancelled afterwards: picked up by reconciliation for a refund
    REFUND_PENDING
}
