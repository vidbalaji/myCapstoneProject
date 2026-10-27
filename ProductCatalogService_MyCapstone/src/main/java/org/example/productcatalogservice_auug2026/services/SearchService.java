package org.example.productcatalogservice_auug2026.services;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import org.example.productcatalogservice_auug2026.models.Category;
import org.example.productcatalogservice_auug2026.models.Product;
import org.example.productcatalogservice_auug2026.repos.ProductRepo;
import org.example.productcatalogservice_auug2026.search.IProductSearchIndexer;
import org.example.productcatalogservice_auug2026.search.ProductDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private ProductRepo productRepo;

    @Autowired(required = false)
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private IProductSearchIndexer productSearchIndexer;

    @Value("${app.search.elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;

    public Page<Product> searchProducts(String query, Integer pageSize, Integer pageNumber) {
        return searchProducts(query, pageSize, pageNumber, null);
    }

    public Page<Product> searchProducts(String query, Integer pageSize, Integer pageNumber, String sortBy) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be empty");
        }
        int size = pageSize == null || pageSize <= 0 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;

        if (elasticsearchEnabled && elasticsearchOperations != null) {
            return searchInElasticsearch(query, PageRequest.of(page, size, toSort(sortBy)));
        }
        return searchInDatabase(query, PageRequest.of(page, size, toDatabaseSort(sortBy)));
    }

    // Full text search across name (boosted), description and category name. fuzziness AUTO
    // tolerates typos, so "lapotp" still finds "laptop".
    private Page<Product> searchInElasticsearch(String query, Pageable pageable) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(query)
                        .fields("name^3", "description", "categoryName^2")
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")))
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        List<Product> products = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(SearchService::from)
                .toList();
        return new PageImpl<>(products, pageable, hits.getTotalHits());
    }

    private Page<Product> searchInDatabase(String query, Pageable pageable) {
        return productRepo.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query, pageable);
    }

    // Re-builds the search index from MySQL (the source of truth) in batches.
    public long reindexAll() {
        if (!elasticsearchEnabled) {
            throw new IllegalArgumentException("Elasticsearch is disabled (app.search.elasticsearch.enabled=false)");
        }
        long count = 0;
        Page<Product> page;
        int pageNumber = 0;
        do {
            page = productRepo.findAll(PageRequest.of(pageNumber++, 1000, Sort.by("id")));
            productSearchIndexer.indexAll(page.getContent());
            count += page.getNumberOfElements();
        } while (page.hasNext());
        return count;
    }

    private Sort toSort(String sortBy) {
        if ("PRICE_ASC".equalsIgnoreCase(sortBy)) return Sort.by("price").ascending();
        if ("PRICE_DESC".equalsIgnoreCase(sortBy)) return Sort.by("price").descending();
        return Sort.unsorted();  // relevance (_score)
    }

    private Sort toDatabaseSort(String sortBy) {
        Sort sort = toSort(sortBy);
        return sort.isSorted() ? sort.and(Sort.by("id").descending())
                : Sort.by("price").descending().and(Sort.by("id").descending());
    }

    private static Product from(ProductDocument document) {
        Product product = new Product();
        product.setId(document.getId());
        product.setName(document.getName());
        product.setDescription(document.getDescription());
        product.setImageUrl(document.getImageUrl());
        product.setPrice(document.getPrice());
        if (document.getCategoryId() != null) {
            Category category = new Category();
            category.setId(document.getCategoryId());
            category.setName(document.getCategoryName());
            product.setCategory(category);
        }
        return product;
    }
}
