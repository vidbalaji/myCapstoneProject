package org.example.productcatalogservice_auug2026.search;

import org.example.productcatalogservice_auug2026.models.Product;

import java.util.List;

public interface IProductSearchIndexer {
    void index(Product product);

    void indexAll(List<Product> products);

    void remove(Long productId);
}
