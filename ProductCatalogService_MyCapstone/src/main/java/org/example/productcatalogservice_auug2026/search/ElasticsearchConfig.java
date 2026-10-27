package org.example.productcatalogservice_auug2026.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

// Only wire Elasticsearch repositories (which create the index on startup) when search is switched on,
// so the service still starts on a laptop with just MySQL.
@Configuration
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
@EnableElasticsearchRepositories(basePackageClasses = ProductSearchRepository.class)
public class ElasticsearchConfig {
}
