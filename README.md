# 08 — MongoDB Deep Dive

**Difficulty:** Intermediate (5/10) · **Java 21** · **Spring Boot 3.2.5**

MongoDB'nin Spring Boot ile kullanımını, document modelini, aggregation pipeline'ı ve MongoTemplate'i kapsar.

---

## Kapsanan Konular

### Document Model
| Kavram | Açıklama |
|--------|----------|
| `@Document` | Collection mapping |
| `@Id` | ObjectId (String) |
| `@TextIndexed` | Full-text search index |
| `@CompoundIndex` | Compound index (category + price) |
| `@Indexed` | Single field index |
| `@Field` | Alan adı override |
| Embedded Record | `Review` record — embedded array of objects |

### Spring Data MongoDB — Repository
```
findByCategory(String category)                    → method naming
findByCategoryAndActiveTrue(String category)       → boolean field
findByPriceBetween(double min, double max)         → range
findByTagsContaining(String tag)                   → array element
Page<Product> findByCategory(String, Pageable)     → pagination
@Query("{ 'category': ?0, 'price': { $lte: ?1 } }") → custom JSON query
@Query("{ $text: { $search: ?0 } }")               → full-text search
@Query(value = "...", fields = "{ 'name': 1 }")    → projection
```

### MongoTemplate — Dynamic Queries
```java
// Criteria API — runtime'da koşul ekle
Query query = new Query();
query.addCriteria(Criteria.where("category").is(category));
query.addCriteria(Criteria.where("price").gte(minPrice).lte(maxPrice));
query.addCriteria(Criteria.where("tags").in(tag));
mongoTemplate.find(query, Product.class);
```

### Atomic Operations
```java
// decreaseStock — race condition yok, tek atomik operasyon
Query q = Query.query(Criteria.where("_id").is(id).and("stock").gte(qty));
Update u = new Update().inc("stock", -qty);
mongoTemplate.updateFirst(q, u, Product.class);  // returns ModifiedCount
```

### Embedded Document Push
```java
// addReview — $push operatörü
Update update = new Update()
    .push("reviews", review)
    .inc("reviewCount", 1);
```

### Aggregation Pipeline

#### 1. Category Stats (`$group`, `$match`, `$sort`)
```
MATCH active=true → GROUP BY category → count, avg/min/max price, totalStock → SORT DESC
```

#### 2. Top Tags (`$unwind`, `$group`)
```
MATCH active=true → UNWIND tags (array → rows) → GROUP BY tag → count → LIMIT N
```

#### 3. Price Distribution (`$bucket`)
```
Boundaries: [0, 10K, 25K, 50K, 100K, "premium"]
→ count + totalValue per bucket
```

#### 4. Avg Rating (`$unwind` reviews, `$group`, `$match`)
```
MATCH reviews exists → UNWIND reviews → GROUP BY _id → avgRating → MATCH ≥4.0
```

---

## REST Endpoints

| Method | URL | Açıklama |
|--------|-----|----------|
| POST | `/api/products` | Ürün oluştur |
| GET | `/api/products/{id}` | Ürün getir |
| GET | `/api/products?page=0&size=10&sortBy=price` | Sayfalı liste |
| GET | `/api/products/search?category=&minPrice=&maxPrice=&tag=&active=` | Dinamik filtre |
| PATCH | `/api/products/{id}/stock/decrease?quantity=3` | Atomik stok düşür |
| POST | `/api/products/{id}/reviews` | Yorum ekle |
| GET | `/api/products/stats/categories` | Kategori istatistikleri |
| GET | `/api/products/stats/tags?limit=10` | En popüler tag'ler |
| GET | `/api/products/stats/price-distribution` | Fiyat dağılımı |
| GET | `/api/products/stats/avg-rating` | Ort. puan ≥ 4.0 ürünler |

---

## Mülakat Soruları

**Q: MongoDB'de Embedding vs Referencing ne zaman kullanılır?**
A: Embed — birlikte okunacak, sık değişmeyen (adres, özellikler, yorumlar). Reference — çok-çok ilişki, bağımsız yaşam döngüsü (kategori, marka).

**Q: MongoRepository ile MongoTemplate farkı nedir?**
A: MongoRepository basit CRUD + method naming için. MongoTemplate runtime'da dynamic query, aggregation, atomic ops için daha güçlü.

**Q: `updateFirst` neden race-safe?**
A: MongoDB'de `findAndModify` / `update` tek atomik operasyon — koşul + güncelleme aynı anda çalışır, arada başka işlem giremez.

**Q: `$unwind` ne işe yarar?**
A: Array alanını "açar" — her eleman için ayrı bir döküman üretir. Tag/review bazlı group/aggregate için zorunlu.

**Q: `$bucket` nedir?**
A: Sayısal değerleri tanımlı aralıklara (bucket) dağıtır — histogram gibi. `withDefaultBucket` sınır dışı değerler için.

---

## Çalıştırma

```bash
# MongoDB başlat
docker run -d -p 27017:27017 --name mongo mongo:7

# Uygulama başlat
mvn spring-boot:run

# Test
mvn test
```

**application.yml**
```yaml
spring.data.mongodb.uri: mongodb://localhost:27017/ecommerce
spring.data.mongodb.auto-index-creation: true
```
