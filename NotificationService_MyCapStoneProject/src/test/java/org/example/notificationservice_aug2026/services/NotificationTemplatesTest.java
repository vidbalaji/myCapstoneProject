package org.example.notificationservice_aug2026.services;

import org.example.notificationservice_aug2026.dtos.OrderEventDto;
import org.example.notificationservice_aug2026.dtos.OrderItemDto;
import org.example.notificationservice_aug2026.dtos.PaymentEventDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTemplatesTest {

    @Test
    void orderPlaced_ListsItemsAndTotal() {
        OrderItemDto item = new OrderItemDto();
        item.setProductName("Kettle");
        item.setQuantity(2);
        item.setUnitPrice(999.5);
        OrderEventDto event = new OrderEventDto();
        event.setEventType("ORDER_PLACED");
        event.setOrderId(7L);
        event.setCurrency("INR");
        event.setTotalAmount(1999.0);
        event.setPaymentMethod("UPI");
        event.setItems(List.of(item));

        NotificationTemplates.Message message = NotificationTemplates.forOrderEvent(event);
        assertEquals("Order #7 confirmed", message.subject());
        assertTrue(message.body().contains("Kettle x 2 @ 999.50"));
        assertTrue(message.body().contains("Total: INR 1999.00"));
    }

    @Test
    void paymentSucceeded_IsAReceipt_InRupees() {
        PaymentEventDto event = new PaymentEventDto();
        event.setEventType("PAYMENT_SUCCEEDED");
        event.setOrderId(7L);
        event.setAmount(199900L);
        event.setCurrency("INR");
        event.setReceiptNumber("RCPT-1");
        event.setGateway("RAZORPAY");

        NotificationTemplates.Message message = NotificationTemplates.forPaymentEvent(event);
        assertEquals("Payment receipt RCPT-1", message.subject());
        assertTrue(message.body().contains("INR 1999.00"));
    }

    @Test
    void paidStatusChange_IsNotEmailedTwice() {
        OrderEventDto event = new OrderEventDto();
        event.setEventType("ORDER_STATUS_CHANGED");
        event.setOrderId(9L);
        event.setStatus("PAID");
        assertNull(NotificationTemplates.forOrderEvent(event));

        event.setStatus("OUT_FOR_DELIVERY");
        assertEquals("Order #9 is now out for delivery", NotificationTemplates.forOrderEvent(event).subject());
    }
}
