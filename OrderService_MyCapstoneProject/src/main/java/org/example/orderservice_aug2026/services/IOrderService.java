package org.example.orderservice_aug2026.services;

import org.example.orderservice_aug2026.dtos.PaymentEventDto;
import org.example.orderservice_aug2026.models.Address;
import org.example.orderservice_aug2026.models.Order;
import org.example.orderservice_aug2026.models.OrderStatus;
import org.example.orderservice_aug2026.models.PaymentMethod;
import org.springframework.data.domain.Page;

public interface IOrderService {
    Order checkout(Long userId, String userEmail, Address deliveryAddress, PaymentMethod paymentMethod);

    Order getOrder(Long orderId, Long userId);

    Page<Order> getOrderHistory(Long userId, Integer pageNumber, Integer pageSize);

    Order updateStatus(Long orderId, OrderStatus newStatus, String note);

    Order cancelOrder(Long orderId, Long userId);

    void handlePaymentEvent(PaymentEventDto paymentEvent);
}
