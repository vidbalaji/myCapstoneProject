package org.example.productcatalogservice_auug2026.repos;

import org.example.productcatalogservice_auug2026.models.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepo extends JpaRepository<Product,Long> {

    Optional<Product> findById(Long id);

    Product save(Product product);

    void deleteById(Long id);

    Page<Product> findByName(String name, Pageable pageable);

    // Keyword search fallback used when Elasticsearch is switched off: WHERE name LIKE %q% OR description LIKE %q%
    Page<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description,
                                                                                 Pageable pageable);

    Page<Product> findByCategory_Id(Long categoryId, Pageable pageable);
}
