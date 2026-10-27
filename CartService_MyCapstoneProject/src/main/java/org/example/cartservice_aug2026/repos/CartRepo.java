package org.example.cartservice_aug2026.repos;

import org.example.cartservice_aug2026.models.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CartRepo extends MongoRepository<Cart, Long> {
}
