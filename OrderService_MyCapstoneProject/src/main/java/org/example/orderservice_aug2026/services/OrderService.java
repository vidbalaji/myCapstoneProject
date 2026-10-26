package org.example.orderservice_aug2026.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.orderservice_aug2026.clients.CartServiceClient;
import org.example.orderservice_aug2026.clients.KafkaProducerClient;
import org.example.orderservice_aug2026.clients.ProductServiceClient;
import org.example.orderservice_aug2026.dtos.*;
import org.example.orderservice_aug2026.exceptions.InvalidOrderStateException;
import org.example.orderservice_aug2026.exceptions.OrderNotFoundException;
import org.example.orderservice_aug2026.models.*;
import org.example.orderservice_aug2026.repos.OrderRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

@Service
public class OrderService implements IOrderService {

    public static final String ORDER_EVENTS_TOPIC = "order-events";

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private CartServiceClient cartServiceClient;

    @Autowired
    private ProductServiceClient productServiceClient;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public Order checkout(Long userId, String userEmail, Address deliveryAddress, PaymentMethod paymentMethod) {
        validateAddress(deliveryAddress);
        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required");
        }

        CartDto cart = cartServiceClient.getCart(userId);
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setUserEmail(userEmail);
        order.setDeliveryAddress(deliveryAddress);
        order.setPaymentMethod(paymentMethod);

        double total = 0;
        for (CartItemDto cartItem : cart.getItems()) {
            // Charge the current catalog price, not the one cached in the cart when the item was added
            ProductDto product = productServiceClient.getProduct(cartItem.getProductId());
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(cartItem.getQuantity());
            order.getItems().add(item);
            total += product.getPrice() * cartItem.getQuantity();
        }
        order.setTotalAmount(Math.round(total * 100) / 100.0);
        changeStatus(order, OrderStatus.PAYMENT_PENDING, "Order placed, awaiting payment");

        Order saved = orderRepo.save(order);
        publishAfterCommit("ORDER_PLACED", saved);

        // The order is already committed at this point from the user's perspective; a failure to empty the
        // cart is logged rather than failing checkout (the user can clear it themselves).
        runAfterCommit(() -> {
            try {
                cartServiceClient.clearCart(userId);
            } catch (RuntimeException exception) {
                log.warn("Order {} placed but cart for user {} was not cleared: {}", saved.getId(), userId,
                        exception.getMessage());
            }
        });
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId, Long userId) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));
        // initialise lazy collections while the session is open
        order.getItems().size();
        order.getStatusHistory().size();
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getOrderHistory(Long userId, Integer pageNumber, Integer pageSize) {
        Page<Order> page = orderRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(pageNumber, pageSize));
        page.forEach(order -> order.getItems().size());
        return page;
    }

    @Override
    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus, String note) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));
        moveTo(order, newStatus, note);
        return order;
    }

    @Override
    @Transactional
    public Order cancelOrder(Long orderId, Long userId) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));
        if (!EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_FAILED, OrderStatus.PAID)
                .contains(order.getStatus())) {
            throw new InvalidOrderStateException("Order can no longer be cancelled; it is " + order.getStatus());
        }
        moveTo(order, OrderStatus.CANCELLED, "Cancelled by customer");
        return order;
    }

    @Override
    @Transactional
    public void handlePaymentEvent(PaymentEventDto paymentEvent) {
        Order order = orderRepo.findById(paymentEvent.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Payment event for unknown order {}", paymentEvent.getOrderId());
            return;
        }
        switch (paymentEvent.getEventType()) {
            case "PAYMENT_LINK_CREATED" -> {
                order.setPaymentLink(paymentEvent.getPaymentLink());
                order.setLastUpdatedAt(new Date());
            }
            case "PAYMENT_SUCCEEDED" -> {
                // Kafka delivers at-least-once: ignore a duplicate success for an order that is already paid
                if (order.getStatus().canMoveTo(OrderStatus.PAID)) {
                    moveTo(order, OrderStatus.PAID, "Payment received via " + paymentEvent.getGateway());
                }
            }
            case "PAYMENT_FAILED" -> {
                if (order.getStatus().canMoveTo(OrderStatus.PAYMENT_FAILED)) {
                    moveTo(order, OrderStatus.PAYMENT_FAILED, "Payment failed");
                }
            }
            default -> log.debug("Ignoring payment event {}", paymentEvent.getEventType());
        }
    }

    private void moveTo(Order order, OrderStatus newStatus, String note) {
        if (!order.getStatus().canMoveTo(newStatus)) {
            throw new InvalidOrderStateException("Cannot move order " + order.getId() + " from "
                    + order.getStatus() + " to " + newStatus);
        }
        changeStatus(order, newStatus, note);
        publishAfterCommit(newStatus == OrderStatus.CANCELLED ? "ORDER_CANCELLED" : "ORDER_STATUS_CHANGED", order);
    }

    private void changeStatus(Order order, OrderStatus newStatus, String note) {
        Date now = new Date();
        order.setStatus(newStatus);
        order.setLastUpdatedAt(now);
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(newStatus);
        history.setNote(note);
        history.setChangedAt(now);
        order.getStatusHistory().add(history);
    }

    private void validateAddress(Address address) {
        if (address == null || isBlank(address.getFullName()) || isBlank(address.getLine1())
                || isBlank(address.getCity()) || isBlank(address.getPincode())) {
            throw new IllegalArgumentException("Delivery address needs fullName, line1, city and pincode");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Events go out only once the DB transaction has committed, so consumers never see an order
    // that was rolled back.
    private void publishAfterCommit(String eventType, Order order) {
        OrderEventDto event = toEvent(eventType, order);
        runAfterCommit(() -> {
            try {
                kafkaProducerClient.sendMessage(ORDER_EVENTS_TOPIC, String.valueOf(event.getOrderId()),
                        objectMapper.writeValueAsString(event));
            } catch (JsonProcessingException | RuntimeException exception) {
                log.error("Could not publish {} for order {}: {}", eventType, event.getOrderId(), exception.getMessage());
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private OrderEventDto toEvent(String eventType, Order order) {
        OrderEventDto event = new OrderEventDto();
        event.setEventType(eventType);
        event.setOrderId(order.getId());
        event.setUserId(order.getUserId());
        event.setUserEmail(order.getUserEmail());
        event.setStatus(order.getStatus().name());
        event.setTotalAmount(order.getTotalAmount());
        event.setCurrency(order.getCurrency());
        event.setPaymentMethod(order.getPaymentMethod() == null ? null : order.getPaymentMethod().name());
        List<OrderItemDto> items = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            OrderItemDto itemDto = new OrderItemDto();
            itemDto.setProductId(item.getProductId());
            itemDto.setProductName(item.getProductName());
            itemDto.setUnitPrice(item.getUnitPrice());
            itemDto.setQuantity(item.getQuantity());
            items.add(itemDto);
        }
        event.setItems(items);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }
}
