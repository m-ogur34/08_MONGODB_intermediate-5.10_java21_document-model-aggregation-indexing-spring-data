package com.mongodb.service;

import com.mongodb.model.Product;
import com.mongodb.repository.ProductRepository;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * MongoDB Operations:
 *   - MongoRepository → basit CRUD
 *   - MongoTemplate   → karmaşık query, aggregation, atomic ops
 */
@Service
public class ProductService {

    private final ProductRepository repository;
    private final MongoTemplate mongoTemplate;

    public ProductService(ProductRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    // ----------------------------------------------------------------
    // CRUD
    // ----------------------------------------------------------------
    public Product create(Product product) {
        return repository.save(product);
    }

    public Optional<Product> findById(String id) {
        return repository.findById(id);
    }

    public Page<Product> findAll(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));
        return repository.findAll(pageable);
    }

    // ----------------------------------------------------------------
    // MongoTemplate — Dinamik sorgular
    // ----------------------------------------------------------------
    public List<Product> search(String category, Double minPrice, Double maxPrice,
                                 String tag, Boolean active) {
        Query query = new Query();

        if (category != null)  query.addCriteria(Criteria.where("category").is(category));
        if (minPrice != null)  query.addCriteria(Criteria.where("price").gte(minPrice));
        if (maxPrice != null)  query.addCriteria(Criteria.where("price").lte(maxPrice));
        if (tag != null)       query.addCriteria(Criteria.where("tags").in(tag));
        if (active != null)    query.addCriteria(Criteria.where("active").is(active));

        query.with(Sort.by(Sort.Direction.ASC, "price"));
        return mongoTemplate.find(query, Product.class);
    }

    // ----------------------------------------------------------------
    // Atomic Güncelleme — Yarış koşulu olmadan stok düşürme
    // ----------------------------------------------------------------
    public boolean decreaseStock(String productId, int quantity) {
        Query query = Query.query(
                Criteria.where("_id").is(productId)
                        .and("stock").gte(quantity));

        Update update = new Update().inc("stock", -quantity);
        var result = mongoTemplate.updateFirst(query, update, Product.class);
        return result.getModifiedCount() > 0;
    }

    // ----------------------------------------------------------------
    // Review Ekleme (Embedded document push)
    // ----------------------------------------------------------------
    public void addReview(String productId, Product.Review review) {
        Query query = Query.query(Criteria.where("_id").is(productId));
        Update update = new Update()
                .push("reviews", review)
                .inc("reviewCount", 1);
        mongoTemplate.updateFirst(query, update, Product.class);
    }

    // ----------------------------------------------------------------
    // AGGREGATION PIPELINE
    // ----------------------------------------------------------------

    // Kategori bazlı istatistikler
    public List<Map> getCategoryStats() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("active").is(true)),
                Aggregation.group("category")
                        .count().as("productCount")
                        .avg("price").as("avgPrice")
                        .min("price").as("minPrice")
                        .max("price").as("maxPrice")
                        .sum("stock").as("totalStock"),
                Aggregation.sort(Sort.Direction.DESC, "productCount"),
                Aggregation.project("productCount", "avgPrice", "minPrice", "maxPrice", "totalStock")
                        .and("_id").as("category")
        );

        return mongoTemplate.aggregate(agg, "products", Map.class).getMappedResults();
    }

    // En çok kullanılan tag'ler
    public List<Map> getTopTags(int limit) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("active").is(true)),
                Aggregation.unwind("tags"),
                Aggregation.group("tags").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(limit),
                Aggregation.project("count").and("_id").as("tag")
        );

        return mongoTemplate.aggregate(agg, "products", Map.class).getMappedResults();
    }

    // Fiyat dağılımı — $bucket
    public List<Map> getPriceDistribution() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.bucket("price")
                        .withBoundaries(0, 10000, 25000, 50000, 100000)
                        .withDefaultBucket("premium")
                        .andOutputCount().as("count")
                        .andOutput("price").sum().as("totalValue")
        );

        return mongoTemplate.aggregate(agg, "products", Map.class).getMappedResults();
    }

    // Ortalama puan hesapla (embedded reviews)
    public List<Map> getProductsWithAvgRating() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("reviews").exists(true)),
                Aggregation.unwind("reviews"),
                Aggregation.group("_id")
                        .first("name").as("name")
                        .avg("reviews.rating").as("avgRating")
                        .count().as("reviewCount"),
                Aggregation.match(Criteria.where("avgRating").gte(4.0)),
                Aggregation.sort(Sort.Direction.DESC, "avgRating")
        );

        return mongoTemplate.aggregate(agg, "products", Map.class).getMappedResults();
    }
}
