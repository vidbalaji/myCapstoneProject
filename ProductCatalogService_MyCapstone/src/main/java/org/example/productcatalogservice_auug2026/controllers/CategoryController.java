package org.example.productcatalogservice_auug2026.controllers;

import org.example.productcatalogservice_auug2026.dtos.CategoryDto;
import org.example.productcatalogservice_auug2026.dtos.ProductDto;
import org.example.productcatalogservice_auug2026.models.Category;
import org.example.productcatalogservice_auug2026.models.Product;
import org.example.productcatalogservice_auug2026.repos.CategoryRepo;
import org.example.productcatalogservice_auug2026.services.IProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    @Autowired
    private CategoryRepo categoryRepo;

    @Autowired
    private IProductService productService;

    @GetMapping
    public List<CategoryDto> getAllCategories() {
        return categoryRepo.findAll(Sort.by("name")).stream().map(this::from).toList();
    }

    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CategoryDto categoryDto) {
        if (categoryDto.getName() == null || categoryDto.getName().isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (categoryRepo.findByNameIgnoreCase(categoryDto.getName()).isPresent()) {
            throw new IllegalArgumentException("Category " + categoryDto.getName() + " already exists");
        }
        Category category = new Category();
        category.setName(categoryDto.getName());
        category.setDescription(categoryDto.getDescription());
        return new ResponseEntity<>(from(categoryRepo.save(category)), HttpStatus.CREATED);
    }

    // Browse products by category (PRD 2.1)
    @GetMapping("/{id}/products")
    public Page<ProductDto> getProductsInCategory(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "0") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer size) {
        if (page < 0 || size <= 0 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and 100");
        }
        return productService.getProductsByCategory(id, page, size).map(this::from);
    }

    private CategoryDto from(Category category) {
        CategoryDto categoryDto = new CategoryDto();
        categoryDto.setId(category.getId());
        categoryDto.setName(category.getName());
        categoryDto.setDescription(category.getDescription());
        return categoryDto;
    }

    private ProductDto from(Product product) {
        ProductDto productDto = new ProductDto();
        productDto.setId(product.getId());
        productDto.setName(product.getName());
        productDto.setDescription(product.getDescription());
        productDto.setPrice(product.getPrice());
        productDto.setImageUrl(product.getImageUrl());
        if (product.getCategory() != null) {
            productDto.setCategory(from(product.getCategory()));
        }
        return productDto;
    }
}
