package org.example.orderservice_aug2026.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
// "order" is a reserved word in SQL. The composite index serves "my orders, newest first".
@Table(name = "orders", indexes = @Index(name = "idx_orders_user_created", columnList = "userId, createdAt"))
public class Order extends BaseModel {
    private Long userId;

    private String userEmail;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Double totalAmount;

    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(length = 1024)
    private String paymentLink;

    @Embedded
    private Address deliveryAddress;

    // @BatchSize avoids the N+1 problem on the order-history page: the items of up to 50 orders are loaded
    // with one "WHERE order_id IN (...)" query instead of one query per order
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    @BatchSize(size = 50)
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();
}
