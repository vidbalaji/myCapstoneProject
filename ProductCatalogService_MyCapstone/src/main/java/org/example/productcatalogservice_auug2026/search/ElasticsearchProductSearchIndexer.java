package org.example.productcatalogservice_auug2026.search;

import org.example.productcatalogservice_auug2026.models.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchProductSearchIndexer implements IProductSearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchProductSearchIndexer.class);

    @Autowired
    private ProductSearchRepository productSearchRepository;

    // MySQL is the source of truth. An indexing failure must not fail the product write,
    // so it is logged and can be repaired with POST /search/reindex.
    @Override
    public void index(Product product) {
        try {
            productSearchRepository.save(from(product));
        } catch (RuntimeException exception) {
            log.warn("Could not index product {} in Elasticsearch: {}", product.getId(), exception.getMessage());
        }
    }

    @Override
    public void indexAll(List<Product> products) {
        productSearchRepository.saveAll(products.stream().map(ElasticsearchProductSearchIndexer::from).toList());
    }

    @Override
    public void remove(Long productId) {
        try {
            productSearchRepository.deleteById(productId);
        } catch (RuntimeException exception) {
            log.warn("Could not remove product {} from Elasticsearch: {}", productId, exception.getMessage());
        }
    }

    public static ProductDocument from(Product product) {
        ProductDocument document = new ProductDocument();
        document.setId(product.getId());
        document.setName(product.getName());
        document.setDescription(product.getDescription());
        document.setImageUrl(product.getImageUrl());
        document.setPrice(product.getPrice());
        if (product.getCategory() != null) {
            document.setCategoryId(product.getCategory().getId());
            document.setCategoryName(product.getCategory().getName());
        }
        return document;
    }
}
