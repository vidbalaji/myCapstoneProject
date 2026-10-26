package org.example.orderservice_aug2026.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();
}
