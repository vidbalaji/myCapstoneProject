package org.example.notificationservice_aug2026.services;

import org.example.notificationservice_aug2026.dtos.OrderEventDto;
import org.example.notificationservice_aug2026.dtos.OrderItemDto;
import org.example.notificationservice_aug2026.dtos.PaymentEventDto;

import java.util.Locale;

// Turns domain events into (subject, body) pairs. Returns null when an event needs no email.
public final class NotificationTemplates {

    public record Message(String subject, String body) {
    }

    private NotificationTemplates() {
    }

    public static Message forOrderEvent(OrderEventDto event) {
        return switch (event.getEventType()) {
            case "ORDER_PLACED" -> {
                StringBuilder body = new StringBuilder("Thank you for your order #" + event.getOrderId() + ".\n\n");
                if (event.getItems() != null) {
                    for (OrderItemDto item : event.getItems()) {
                        body.append(String.format(Locale.ROOT, "- %s x %d @ %.2f%n",
                                item.getProductName(), item.getQuantity(), item.getUnitPrice()));
                    }
                }
                body.append(String.format(Locale.ROOT, "%nTotal: %s %.2f%nPayment method: %s%n",
                        event.getCurrency(), event.getTotalAmount(), event.getPaymentMethod()));
                body.append("We will email you a payment link shortly.");
                yield new Message("Order #" + event.getOrderId() + " confirmed", body.toString());
            }
            case "ORDER_STATUS_CHANGED" -> {
                if ("PAID".equals(event.getStatus()) || "PAYMENT_FAILED".equals(event.getStatus())) {
                    yield null;   // the payment email already covers these
                }
                yield new Message("Order #" + event.getOrderId() + " is now " + readable(event.getStatus()),
                        "Your order #" + event.getOrderId() + " status changed to " + readable(event.getStatus()) + ".");
            }
            case "ORDER_CANCELLED" -> new Message("Order #" + event.getOrderId() + " cancelled",
                    "Your order #" + event.getOrderId() + " has been cancelled. Any amount paid will be refunded.");
            default -> null;
        };
    }

    public static Message forPaymentEvent(PaymentEventDto event) {
        String amount = event.getAmount() == null ? "" :
                String.format(Locale.ROOT, "%s %.2f", event.getCurrency(), event.getAmount() / 100.0);
        return switch (event.getEventType()) {
            case "PAYMENT_LINK_CREATED" -> new Message("Complete payment for order #" + event.getOrderId(),
                    "Pay " + amount + " for order #" + event.getOrderId() + " here: " + event.getPaymentLink());
            case "PAYMENT_SUCCEEDED" -> new Message("Payment receipt " + event.getReceiptNumber(),
                    "Receipt: " + event.getReceiptNumber() + "\nOrder: #" + event.getOrderId()
                            + "\nAmount paid: " + amount + "\nPaid via: " + event.getGateway()
                            + "\n\nThank you for shopping with us!");
            case "PAYMENT_FAILED" -> new Message("Payment failed for order #" + event.getOrderId(),
                    "We could not process the payment of " + amount + " for order #" + event.getOrderId()
                            + ". Please try again from your orders page.");
            default -> null;
        };
    }

    private static String readable(String status) {
        return status == null ? "" : status.replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
