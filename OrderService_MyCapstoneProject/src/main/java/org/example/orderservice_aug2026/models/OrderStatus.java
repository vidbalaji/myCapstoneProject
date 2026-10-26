package org.example.orderservice_aug2026.models;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAID,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    // The order lifecycle as a state machine: any transition not listed here is rejected
    public Set<OrderStatus> allowedNextStatuses() {
        return switch (this) {
            case PAYMENT_PENDING -> EnumSet.of(PAID, PAYMENT_FAILED, CANCELLED);
            case PAYMENT_FAILED -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(OUT_FOR_DELIVERY, DELIVERED);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus next) {
        return allowedNextStatuses().contains(next);
    }
}
