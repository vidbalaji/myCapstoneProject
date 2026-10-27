package org.example.orderservice_aug2026.controllers;

import org.example.orderservice_aug2026.dtos.*;
import org.example.orderservice_aug2026.models.*;
import org.example.orderservice_aug2026.services.IOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

// X-User-Id / X-User-Email are added by the API Gateway after validating the caller's token
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private IOrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<OrderDto> checkout(@RequestHeader("X-User-Id") Long userId,
                                             @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                             @RequestBody CheckoutRequestDto checkoutRequestDto) {
        Order order = orderService.checkout(userId, userEmail,
                from(checkoutRequestDto.getDeliveryAddress()),
                toPaymentMethod(checkoutRequestDto.getPaymentMethod()));
        return new ResponseEntity<>(from(order), HttpStatus.CREATED);
    }

    // Order history (PRD 4.2)
    @GetMapping
    public Page<OrderDto> getMyOrders(@RequestHeader("X-User-Id") Long userId,
                                      @RequestParam(defaultValue = "0") Integer page,
                                      @RequestParam(defaultValue = "10") Integer size) {
        if (page < 0 || size <= 0 || size > 50) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and 50");
        }
        return orderService.getOrderHistory(userId, page, size).map(this::from);
    }

    @GetMapping("/{id}")
    public OrderDto getOrder(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        return from(orderService.getOrder(id, userId));
    }

    // Order tracking (PRD 4.3)
    @GetMapping("/{id}/tracking")
    public TrackingDto trackOrder(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        Order order = orderService.getOrder(id, userId);
        TrackingDto trackingDto = new TrackingDto();
        trackingDto.setOrderId(order.getId());
        trackingDto.setCurrentStatus(order.getStatus().name());
        List<StatusHistoryDto> history = new ArrayList<>();
        for (OrderStatusHistory entry : order.getStatusHistory()) {
            StatusHistoryDto historyDto = new StatusHistoryDto();
            historyDto.setStatus(entry.getStatus().name());
            historyDto.setNote(entry.getNote());
            historyDto.setChangedAt(entry.getChangedAt());
            history.add(historyDto);
        }
        trackingDto.setHistory(history);
        return trackingDto;
    }

    @PostMapping("/{id}/cancel")
    public OrderDto cancelOrder(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        return from(orderService.cancelOrder(id, userId));
    }

    // Fulfilment updates (SHIPPED, OUT_FOR_DELIVERY, DELIVERED). The gateway only lets ADMIN users call this.
    @PatchMapping("/{id}/status")
    public OrderDto updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequestDto updateStatusRequestDto) {
        OrderStatus status;
        try {
            status = OrderStatus.valueOf(updateStatusRequestDto.getStatus());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Unknown status " + updateStatusRequestDto.getStatus());
        }
        return from(orderService.updateStatus(id, status, updateStatusRequestDto.getNote()));
    }

    private PaymentMethod toPaymentMethod(String value) {
        try {
            return PaymentMethod.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("paymentMethod must be one of CARD, UPI, NET_BANKING, WALLET");
        }
    }

    private Address from(AddressDto addressDto) {
        if (addressDto == null) return null;
        Address address = new Address();
        address.setFullName(addressDto.getFullName());
        address.setLine1(addressDto.getLine1());
        address.setLine2(addressDto.getLine2());
        address.setCity(addressDto.getCity());
        address.setState(addressDto.getState());
        address.setPincode(addressDto.getPincode());
        address.setPhoneNumber(addressDto.getPhoneNumber());
        return address;
    }

    private OrderDto from(Order order) {
        OrderDto orderDto = new OrderDto();
        orderDto.setId(order.getId());
        orderDto.setStatus(order.getStatus().name());
        orderDto.setTotalAmount(order.getTotalAmount());
        orderDto.setCurrency(order.getCurrency());
        orderDto.setPaymentMethod(order.getPaymentMethod().name());
        orderDto.setPaymentLink(order.getPaymentLink());
        orderDto.setCreatedAt(order.getCreatedAt());
        Address address = order.getDeliveryAddress();
        if (address != null) {
            AddressDto addressDto = new AddressDto();
            addressDto.setFullName(address.getFullName());
            addressDto.setLine1(address.getLine1());
            addressDto.setLine2(address.getLine2());
            addressDto.setCity(address.getCity());
            addressDto.setState(address.getState());
            addressDto.setPincode(address.getPincode());
            addressDto.setPhoneNumber(address.getPhoneNumber());
            orderDto.setDeliveryAddress(addressDto);
        }
        List<OrderItemDto> items = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            OrderItemDto itemDto = new OrderItemDto();
            itemDto.setProductId(item.getProductId());
            itemDto.setProductName(item.getProductName());
            itemDto.setUnitPrice(item.getUnitPrice());
            itemDto.setQuantity(item.getQuantity());
            items.add(itemDto);
        }
        orderDto.setItems(items);
        return orderDto;
    }
}
