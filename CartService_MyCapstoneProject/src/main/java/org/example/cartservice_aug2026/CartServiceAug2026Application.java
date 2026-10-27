package org.example.cartservice_aug2026;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CartServiceAug2026Application {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceAug2026Application.class, args);
    }

}
