package org.example.productcatalogservice_auug2026.services;

import org.example.productcatalogservice_auug2026.models.Category;
import org.example.productcatalogservice_auug2026.models.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StorageProductServiceTest {

    @Autowired
    private IProductService productService;

    @Autowired
    private SearchService searchService;

    private Product newProduct(String name, String description, double price, String categoryName) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        Category category = new Category();
        category.setName(categoryName);
        product.setCategory(category);
        return product;
    }

    @Test
    void createProduct_ReusesCategoryByName_AndIgnoresClientId() {
        Product first = newProduct("Dell XPS 13", "Ultrabook laptop", 120000, "Laptops-T1");
        first.setId(999L);
        Product savedFirst = productService.createProduct(first);
        Product savedSecond = productService.createProduct(newProduct("HP Spectre", "Convertible laptop", 110000, "laptops-t1"));

        assertNotEquals(999L, savedFirst.getId());
        assertEquals(savedFirst.getCategory().getId(), savedSecond.getCategory().getId());

        Page<Product> inCategory = productService.getProductsByCategory(savedFirst.getCategory().getId(), 0, 10);
        assertEquals(2, inCategory.getTotalElements());
    }

    @Test
    void searchInDatabase_MatchesNameOrDescription_CaseInsensitive() {
        productService.createProduct(newProduct("Galaxy S30", "Android smartphone with AMOLED", 70000, "Phones-T2"));
        productService.createProduct(newProduct("Pixel 12", "Google SMARTPHONE", 65000, "Phones-T2"));
        productService.createProduct(newProduct("Kettle", "Electric kettle", 2000, "Kitchen-T2"));

        Page<Product> result = searchService.searchProducts("smartphone", 10, 0, "PRICE_ASC");
        assertEquals(2, result.getTotalElements());
        assertEquals("Pixel 12", result.getContent().get(0).getName());
    }

    @Test
    void createProduct_WithoutName_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> productService.createProduct(newProduct(" ", "x", 10, "Misc-T3")));
    }

    @Test
    void deleteProduct_RemovesIt_AndUnknownIdFails() {
        Product saved = productService.createProduct(newProduct("Old Mouse", "USB mouse", 500, "Accessories-T4"));
        productService.deleteProduct(saved.getId());
        assertNull(productService.getProductDetailsById(saved.getId()));
        assertThrows(RuntimeException.class, () -> productService.deleteProduct(saved.getId()));
    }
}
