package org.example.productcatalogservice_auug2026.controllers;

import org.example.productcatalogservice_auug2026.dtos.CategoryDto;
import org.example.productcatalogservice_auug2026.dtos.ProductDto;
import org.example.productcatalogservice_auug2026.dtos.SearchRequestDto;
import org.example.productcatalogservice_auug2026.models.Product;
import org.example.productcatalogservice_auug2026.services.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @PostMapping
    public Page<ProductDto> searchProducts(@RequestBody SearchRequestDto searchRequestDto) {
      return  searchService.searchProducts(searchRequestDto.getQuery(),
              searchRequestDto.getPageSize(),
              searchRequestDto.getPageNumber(),
              searchRequestDto.getSortBy()).map(this::from);
    }

    @GetMapping
    public Page<ProductDto> searchProducts(@RequestParam("q") String query,
                                           @RequestParam(defaultValue = "0") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) String sortBy) {
        return searchService.searchProducts(query, size, page, sortBy).map(this::from);
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Long>> reindex() {
        return new ResponseEntity<>(Map.of("indexed", searchService.reindexAll()), HttpStatus.OK);
    }

    private ProductDto from(Product product) {
        ProductDto productDto = new ProductDto();
        productDto.setId(product.getId());
        productDto.setName(product.getName());
        productDto.setDescription(product.getDescription());
        productDto.setPrice(product.getPrice());
        productDto.setImageUrl(product.getImageUrl());
        if(product.getCategory() != null) {
            CategoryDto categoryDto = new CategoryDto();
            categoryDto.setId(product.getCategory().getId());
            categoryDto.setName(product.getCategory().getName());
            productDto.setCategory(categoryDto);
        }
        return productDto;
    }
}
