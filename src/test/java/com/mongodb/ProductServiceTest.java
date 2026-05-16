package com.mongodb;

import com.mongodb.model.Product;
import com.mongodb.repository.ProductRepository;
import com.mongodb.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    ProductService service;

    @Autowired
    ProductRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void createAndFindById() {
        Product p = new Product();
        p.setName("Laptop");
        p.setCategory("electronics");
        p.setPrice(25000);
        p.setStock(10);
        p.setActive(true);

        Product saved = service.create(p);
        assertThat(saved.getId()).isNotNull();

        Optional<Product> found = service.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Laptop");
    }

    @Test
    void searchByCategory() {
        Product p1 = new Product();
        p1.setName("Phone"); p1.setCategory("electronics"); p1.setPrice(10000); p1.setStock(5); p1.setActive(true);
        Product p2 = new Product();
        p2.setName("Shirt"); p2.setCategory("clothing"); p2.setPrice(500); p2.setStock(20); p2.setActive(true);
        service.create(p1);
        service.create(p2);

        List<Product> results = service.search("electronics", null, null, null, true);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Phone");
    }

    @Test
    void decreaseStock_success() {
        Product p = new Product();
        p.setName("TV"); p.setCategory("electronics"); p.setPrice(50000); p.setStock(5); p.setActive(true);
        Product saved = service.create(p);

        boolean result = service.decreaseStock(saved.getId(), 3);
        assertThat(result).isTrue();

        Product updated = service.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(2);
    }

    @Test
    void decreaseStock_insufficient() {
        Product p = new Product();
        p.setName("Camera"); p.setCategory("electronics"); p.setPrice(15000); p.setStock(2); p.setActive(true);
        Product saved = service.create(p);

        boolean result = service.decreaseStock(saved.getId(), 5);
        assertThat(result).isFalse();

        Product unchanged = service.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getStock()).isEqualTo(2);
    }

    @Test
    void findAll_pagination() {
        for (int i = 0; i < 15; i++) {
            Product p = new Product();
            p.setName("Product " + i); p.setCategory("test"); p.setPrice(100 + i); p.setStock(i + 1); p.setActive(true);
            service.create(p);
        }
        Page<Product> page = service.findAll(0, 5, "price");
        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getContent()).hasSize(5);
    }
}
