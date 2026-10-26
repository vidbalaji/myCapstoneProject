package org.example.orderservice_aug2026.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

// Every status change is recorded; this is what order tracking (PRD 4.3) shows to the user
@Setter
@Getter
@Entity
public class OrderStatusHistory extends BaseModel {
    @ManyToOne
    private Order order;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String note;

    private Date changedAt;
}
