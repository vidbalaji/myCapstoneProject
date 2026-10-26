package org.example.productcatalogservice_auug2026.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

// Denormalised copy of a Product kept in Elasticsearch so search never has to touch MySQL
@Setter
@Getter
@Document(indexName = "products")
public class ProductDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "english")
    private String name;

    @Field(type = FieldType.Text, analyzer = "english")
    private String description;

    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Text)
    private String categoryName;
}
