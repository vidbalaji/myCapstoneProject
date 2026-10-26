package org.example.productcatalogservice_auug2026.search;

import org.example.productcatalogservice_auug2026.models.Product;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpProductSearchIndexer implements IProductSearchIndexer {
    @Override
    public void index(Product product) {
    }

    @Override
    public void indexAll(List<Product> products) {
    }

    @Override
    public void remove(Long productId) {
    }
}
