package org.example.orderservice_aug2026.dtos;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class ProductDto {
    private Long id;
    private String name;
    private Double price;
}
