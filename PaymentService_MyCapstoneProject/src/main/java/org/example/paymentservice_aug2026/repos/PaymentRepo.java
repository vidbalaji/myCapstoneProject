package org.example.paymentservice_aug2026.repos;

import org.example.paymentservice_aug2026.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepo extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByGatewayReference(String gatewayReference);
}
