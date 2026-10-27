package org.example.productcatalogservice_auug2026.services;

import org.example.productcatalogservice_auug2026.models.Product;
import org.springframework.data.domain.Page;

public interface IProductService {
    Product getProductDetailsById(Long id);

    Product replaceProduct(Long id, Product inputProduct);

    Product createProduct(Product product);

    Product getProductBasedOnUserRole(Long productId, Long userId);

    Page<Product> getAllProducts(Integer pageNumber, Integer pageSize);

    Page<Product> getProductsByCategory(Long categoryId, Integer pageNumber, Integer pageSize);

    void deleteProduct(Long id);
}
