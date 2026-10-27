package org.example.orderservice_aug2026.services;

import org.example.orderservice_aug2026.clients.CartServiceClient;
import org.example.orderservice_aug2026.clients.KafkaProducerClient;
import org.example.orderservice_aug2026.clients.ProductServiceClient;
import org.example.orderservice_aug2026.dtos.CartDto;
import org.example.orderservice_aug2026.dtos.CartItemDto;
import org.example.orderservice_aug2026.dtos.PaymentEventDto;
import org.example.orderservice_aug2026.dtos.ProductDto;
import org.example.orderservice_aug2026.exceptions.InvalidOrderStateException;
import org.example.orderservice_aug2026.exceptions.OrderNotFoundException;
import org.example.orderservice_aug2026.models.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private IOrderService orderService;

    @MockBean
    private CartServiceClient cartServiceClient;

    @MockBean
    private ProductServiceClient productServiceClient;

    @MockBean
    private KafkaProducerClient kafkaProducerClient;

    private Address address() {
        Address address = new Address();
        address.setFullName("Asha Kumar");
        address.setLine1("12 MG Road");
        address.setCity("Bengaluru");
        address.setState("KA");
        address.setPincode("560001");
        return address;
    }

    private void givenCart(long userId, long productId, int quantity, double cartPrice, double currentPrice) {
        CartItemDto item = new CartItemDto();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(cartPrice);
        CartDto cart = new CartDto();
        cart.setUserId(userId);
        cart.setItems(List.of(item));
        when(cartServiceClient.getCart(userId)).thenReturn(cart);

        ProductDto product = new ProductDto();
        product.setId(productId);
        product.setName("Product " + productId);
        product.setPrice(currentPrice);
        when(productServiceClient.getProduct(productId)).thenReturn(product);
    }

    private PaymentEventDto paymentEvent(String type, long orderId) {
        PaymentEventDto event = new PaymentEventDto();
        event.setEventType(type);
        event.setOrderId(orderId);
        event.setGateway("MOCK");
        return event;
    }

    @Test
    void checkout_CreatesPendingOrderAtCurrentPrice_ClearsCart_AndPublishesEvent() {
        givenCart(1L, 10L, 2, 90.0, 100.0);

        Order order = orderService.checkout(1L, "asha@test.local", address(), PaymentMethod.UPI);

        assertEquals(OrderStatus.PAYMENT_PENDING, order.getStatus());
        assertEquals(200.0, order.getTotalAmount());   // 2 x current price, not the stale cart price
        verify(cartServiceClient).clearCart(1L);
        verify(kafkaProducerClient).sendMessage(eq(OrderService.ORDER_EVENTS_TOPIC), eq(String.valueOf(order.getId())),
                argThat(m -> m.contains("ORDER_PLACED") && m.contains("asha@test.local")));
    }

    @Test
    void checkout_WithEmptyCart_IsRejected() {
        CartDto empty = new CartDto();
        empty.setItems(List.of());
        when(cartServiceClient.getCart(2L)).thenReturn(empty);
        assertThrows(IllegalArgumentException.class,
                () -> orderService.checkout(2L, "x@test.local", address(), PaymentMethod.CARD));
        verifyNoInteractions(kafkaProducerClient);
    }

    @Test
    void paymentSucceeded_MarksOrderPaid_AndDuplicateEventIsIgnored() {
        givenCart(3L, 11L, 1, 50.0, 50.0);
        Order order = orderService.checkout(3L, "c@test.local", address(), PaymentMethod.CARD);

        orderService.handlePaymentEvent(paymentEvent("PAYMENT_SUCCEEDED", order.getId()));
        orderService.handlePaymentEvent(paymentEvent("PAYMENT_SUCCEEDED", order.getId()));

        Order reloaded = orderService.getOrder(order.getId(), 3L);
        assertEquals(OrderStatus.PAID, reloaded.getStatus());
        assertEquals(2, reloaded.getStatusHistory().size());   // PAYMENT_PENDING, PAID
    }

    @Test
    void statusMachine_RejectsSkippingSteps_AndLateCancellation() {
        givenCart(4L, 12L, 1, 75.0, 75.0);
        Order order = orderService.checkout(4L, "d@test.local", address(), PaymentMethod.WALLET);

        assertThrows(InvalidOrderStateException.class,
                () -> orderService.updateStatus(order.getId(), OrderStatus.DELIVERED, "skip"));

        orderService.handlePaymentEvent(paymentEvent("PAYMENT_SUCCEEDED", order.getId()));
        orderService.updateStatus(order.getId(), OrderStatus.SHIPPED, "Handed to courier");

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(order.getId(), 4L));

        orderService.updateStatus(order.getId(), OrderStatus.OUT_FOR_DELIVERY, null);
        Order delivered = orderService.updateStatus(order.getId(), OrderStatus.DELIVERED, null);
        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());
    }

    @Test
    void users_CannotSeeEachOthersOrders() {
        givenCart(5L, 13L, 1, 20.0, 20.0);
        Order order = orderService.checkout(5L, "e@test.local", address(), PaymentMethod.CARD);
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(order.getId(), 6L));
    }

    @Test
    void paymentLinkCreated_IsStoredOnOrder() {
        givenCart(7L, 14L, 1, 20.0, 20.0);
        Order order = orderService.checkout(7L, "f@test.local", address(), PaymentMethod.CARD);
        PaymentEventDto event = paymentEvent("PAYMENT_LINK_CREATED", order.getId());
        event.setPaymentLink("https://pay.example/abc");
        orderService.handlePaymentEvent(event);
        assertEquals("https://pay.example/abc", orderService.getOrder(order.getId(), 7L).getPaymentLink());
    }
}
