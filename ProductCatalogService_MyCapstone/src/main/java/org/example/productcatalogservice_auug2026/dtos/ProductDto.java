package org.example.productcatalogservice_auug2026.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Double price;
    private CategoryDto category;
}
