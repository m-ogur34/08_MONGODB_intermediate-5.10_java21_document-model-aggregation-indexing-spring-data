package com.mongodb.controller;

import com.mongodb.model.Product;
import com.mongodb.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        return ResponseEntity.ok(service.create(product));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> findById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<Product>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "price") String sortBy) {
        return ResponseEntity.ok(service.findAll(page, size, sortBy));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(service.search(category, minPrice, maxPrice, tag, active));
    }

    @PatchMapping("/{id}/stock/decrease")
    public ResponseEntity<Map<String, Object>> decreaseStock(
            @PathVariable String id,
            @RequestParam int quantity) {
        boolean success = service.decreaseStock(id, quantity);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<Void> addReview(
            @PathVariable String id,
            @RequestBody Product.Review review) {
        service.addReview(id, review);
        return ResponseEntity.ok().build();
    }

    // ── Aggregation endpoints ────────────────────────────────────────────────

    @GetMapping("/stats/categories")
    public ResponseEntity<List<Map>> getCategoryStats() {
        return ResponseEntity.ok(service.getCategoryStats());
    }

    @GetMapping("/stats/tags")
    public ResponseEntity<List<Map>> getTopTags(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.getTopTags(limit));
    }

    @GetMapping("/stats/price-distribution")
    public ResponseEntity<List<Map>> getPriceDistribution() {
        return ResponseEntity.ok(service.getPriceDistribution());
    }

    @GetMapping("/stats/avg-rating")
    public ResponseEntity<List<Map>> getProductsWithAvgRating() {
        return ResponseEntity.ok(service.getProductsWithAvgRating());
    }
}
