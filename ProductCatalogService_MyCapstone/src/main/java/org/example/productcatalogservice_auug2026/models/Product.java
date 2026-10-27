package org.example.productcatalogservice_auug2026.models;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class Product extends BaseModel {
    private String name;
    @Column(length = 2000)
    private String description;
    private String imageUrl;
    private Double price;
    @ManyToOne
    @JsonManagedReference
    private Category category;
    private Boolean isSaleEligible;  //This is why we require dto
}
