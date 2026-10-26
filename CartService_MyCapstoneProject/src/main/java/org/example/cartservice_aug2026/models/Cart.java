package org.example.cartservice_aug2026.models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// One document per user. MongoDB suits the cart because its shape (a variable list of
// embedded items) is read and written as a single unit.
@Setter
@Getter
@Document(collection = "carts")
public class Cart {
    @Id
    private Long userId;
    private List<CartItem> items = new ArrayList<>();
    private Date updatedAt = new Date();
}
