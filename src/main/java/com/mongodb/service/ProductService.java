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
    // MongoDB aggregation pipeline = SQL GROUP BY + JOIN + HAVING'in karşılığı.
    // Her stage önceki stage'in çıktısını alır; sıra önemlidir.
    // ----------------------------------------------------------------

    /**
     * Kategori başına ürün sayısı, fiyat istatistikleri, toplam stok.
     *
     * Pipeline:
     *   $match  → sadece aktif ürünler (index kullanır, erken filtre = hız)
     *   $group  → category alanına göre grupla, her grup için aggregate
     *   $sort   → en fazla ürünlü kategori başa gelsin
     *   $project→ _id'yi "category" olarak yeniden adlandır, gereksiz alan gizle
     *
     * SQL karşılığı:
     *   SELECT category, COUNT(*) AS productCount, AVG(price) AS avgPrice ...
     *   FROM products WHERE active = true
     *   GROUP BY category ORDER BY productCount DESC
     */
    public List<Map> getCategoryStats() {
        Aggregation agg = Aggregation.newAggregation(

                // Stage 1 — $match: index'i kullan, pipeline'a az döküman sok
                Aggregation.match(Criteria.where("active").is(true)),

                // Stage 2 — $group: "category" alanına göre grupla
                //   _id = group by key (category değeri olur)
                //   count()  → $sum: 1 ile eşdeğer
                //   avg/min/max/sum → SQL aggregate fonksiyonları
                Aggregation.group("category")
                        .count().as("productCount")
                        .avg("price").as("avgPrice")
                        .min("price").as("minPrice")
                        .max("price").as("maxPrice")
                        .sum("stock").as("totalStock"),

                // Stage 3 — $sort: büyükten küçüğe productCount
                Aggregation.sort(Sort.Direction.DESC, "productCount"),

                // Stage 4 — $project: çıktı şeklini belirle
                //   _id (grup key'i) → "category" olarak yeniden adlandır
                Aggregation.project("productCount", "avgPrice", "minPrice", "maxPrice", "totalStock")
                        .and("_id").as("category")
        );

        // "products" collection'ında çalıştır, sonuçları Map olarak al
        return mongoTemplate.aggregate(agg, "products", Map.class).getMappedResults();
    }

    /**
     * Array alanını patlatıp (unwind) tag bazlı gruplama.
     * $unwind — MongoDB'ye özgü: [{tags: ["a","b"]}] → [{tag:"a"}, {tag:"b"}]
     *
     * Pipeline:
     *   $match  → aktif ürünler
     *   $unwind → tags array'ini aç: her tag için ayrı döküman üret
     *   $group  → tag değerine göre grupla, kaç kez geçtiğini say
     *   $sort   → en çok kullanılan başa
     *   $limit  → ilk N tag
     *   $project→ _id → "tag" olarak yeniden adlandır
     *
     * SQL'de karşılığı yok — JSON array'i tek SQL satırıyla gruplayamazsın.
     */
    public List<Map> getTopTags(int limit) {
        Aggregation agg = Aggregation.newAggregation(

                // Stage 1 — $match: erken filtre
                Aggregation.match(Criteria.where("active").is(true)),

                // Stage 2 — $unwind: ["elektronik","laptop","gaming"] olan
                //   bir dökümanı 3 ayrı döküman haline getirir.
                //   Böylece her tag group'lanabilir hale gelir.
                Aggregation.unwind("tags"),

                // Stage 3 — $group: aynı tag'leri say
                Aggregation.group("tags").count().as("count"),

                // Stage 4 — $sort: en popüler tag başa
                Aggregation.sort(Sort.Direction.DESC, "count"),

                // Stage 5 — $limit: sadece ilk N sonuç
                Aggregation.limit(limit),

                // Stage 6 — $project: _id (tag değeri) → "tag" olarak sun
                Aggregation.project("count").and("_id").as("tag")
        );

        return mongoTemplate.aggregate(agg, "products", Map.class).getMappedResults();
    }

    /**
     * Fiyat aralıklarına göre ürün dağılımı — histogram.
     * $bucket: SQL CASE WHEN ile sınırlı aralıklar yerine MongoDB native.
     *
     * Boundaries: [0, 10K) → "0-10K arası", [10K, 25K) → "10K-25K arası" ...
     * withDefaultBucket("premium"): 100K üzeri ürünler → "premium" bucket'ına gider
     *
     * Her bucket'ta:
     *   count      → kaç ürün var
     *   totalValue → o aralıktaki toplam fiyat
     */
    public List<Map> getPriceDistribution() {
        Aggregation agg = Aggregation.newAggregation(

                // Stage 1 — $bucket: "price" alanını belirtilen sınırlara dağıt
                //   Boundaries inclusive-left, exclusive-right: [0, 10000)
                //   withDefaultBucket: boundaries dışı (>100K) → "premium"
                Aggregation.bucket("price")
                        .withBoundaries(0, 10000, 25000, 50000, 100000)
                        .withDefaultBucket("premium")
                        .andOutputCount().as("count")           // kaç ürün
                        .andOutput("price").sum().as("totalValue") // toplam fiyat
        );

        return mongoTemplate.aggregate(agg, "products", Map.class).getMappedResults();
    }

    /**
     * Embedded reviews dizisini açıp ortalama puan hesapla.
     * İki kez $match: birincisi erken filtre (index), ikincisi aggregate sonrası filtre.
     *
     * Pipeline:
     *   $match(reviews exists) → yorum olan ürünler (FILTER EARLY)
     *   $unwind(reviews)       → her yorum için ayrı döküman
     *   $group(_id)            → ürüne göre topla: name, avgRating, reviewCount
     *   $match(avgRating≥4)    → aggregate SONRASI filtre (SQL: HAVING)
     *   $sort(avgRating DESC)  → en iyi ürünler başa
     *
     * SQL karşılığı:
     *   SELECT p._id, p.name, AVG(r.rating) AS avgRating, COUNT(r.*) AS reviewCount
     *   FROM products p JOIN reviews r ON r.productId = p._id
     *   WHERE p.reviews IS NOT NULL
     *   GROUP BY p._id, p.name
     *   HAVING AVG(r.rating) >= 4.0
     *   ORDER BY avgRating DESC
     */
    public List<Map> getProductsWithAvgRating() {
        Aggregation agg = Aggregation.newAggregation(

                // Stage 1 — $match: reviews alanı olan ürünler (erken eleme)
                Aggregation.match(Criteria.where("reviews").exists(true)),

                // Stage 2 — $unwind: reviews array'ini aç
                //   [{reviews: [{r:5},{r:4}]}] → [{review:{r:5}}, {review:{r:4}}]
                Aggregation.unwind("reviews"),

                // Stage 3 — $group: ürün (_id) bazında topla
                //   first("name") → unwind'den sonra name tekrar eder, ilkini al
                //   avg("reviews.rating") → embedded alanın ortalaması
                Aggregation.group("_id")
                        .first("name").as("name")
                        .avg("reviews.rating").as("avgRating")
                        .count().as("reviewCount"),

                // Stage 4 — $match (post-aggregation = HAVING): 4.0 ve üzeri
                //   Bu noktada "avgRating" artık hesaplanmış bir alan
                Aggregation.match(Criteria.where("avgRating").gte(4.0)),

                // Stage 5 — $sort: en yüksek puanlı başa
                Aggregation.sort(Sort.Direction.DESC, "avgRating")
        );

        return mongoTemplate.aggregate(agg, "products", Map.class).getMappedResults();
    }
}
