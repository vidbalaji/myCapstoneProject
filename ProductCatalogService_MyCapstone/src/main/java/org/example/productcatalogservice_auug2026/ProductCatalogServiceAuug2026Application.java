package org.example.productcatalogservice_auug2026;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
// Serialise Page<T> responses as a stable {content, page{size,number,totalElements,totalPages}} shape
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class ProductCatalogServiceAuug2026Application {

    public static void main(String[] args) {
        SpringApplication.run(ProductCatalogServiceAuug2026Application.class, args);
    }

}
