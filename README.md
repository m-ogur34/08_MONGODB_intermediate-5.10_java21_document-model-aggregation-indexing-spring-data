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
A: Embedding (gömme): Birlikte okunacak, nadiren bağımsız erişilecek, sık değişmeyen veriler — adres, ürün özellikleri, yorumlar. Tek sorgu ile tüm veriyi çek, join yok. Sınır: BSON belgesi max 16MB; büyük ve sürekli büyüyen diziler için uygun değil. Referencing (referans): Çok-çok ilişki, bağımsız yaşam döngüsü, paylaşılan veri — kategori, marka, kullanıcı. Ayrı collection + manuel join (`$lookup`). Karar kuralı: "Bu veriyi daima birlikte mi okuyorum?" → Evet: embed, Hayır: reference. Örnek: Ürün ↔ Yorumlar → embed; Ürün ↔ Kategori → reference (kategori ismi güncellenmeli).

**Q: MongoRepository ile MongoTemplate farkı nedir?**
A: MongoRepository: Spring Data abstraction, basit CRUD + method naming convention (`findByCategory`, `findByPriceBetween`). Repository interface'i yaz, implementasyonu Spring üretir. 80% use case için yeterli. MongoTemplate: Low-level, güçlü API. Runtime'da dinamik `Criteria` oluştur, aggregation pipeline çalıştır, atomik operasyonlar (`updateFirst` + koşullu `inc`). Kullanım: birden fazla null-check ile koşullu query (`search()` metodu), atomic stok düşürme, aggregation. İkisi birlikte kullanılır — MongoRepository default, MongoTemplate gereken yerde.

**Q: Atomik stok düşürme neden önemli? SQL'den farkı nedir?**
A: SQL'de: `SELECT stock → uygulama kontrol → UPDATE stock=stock-N` — iki sorgu, race condition riski: iki thread aynı anda `stock=10` okur, ikisi de 3 düşürür, sonuç 7 olmalı ama 7 gelir (aslında bir düşürme kaybedildi). MongoDB atomic update: `Query: {stock: {$gte: 3}}` + `Update: {$inc: {stock: -3}}` tek atomik operasyon — MongoDB document-level lock uygular, arada başka işlem giremez. `getModifiedCount() == 0` → stok yetersizdi (false dönülür). SQL karşılığı: `UPDATE products SET stock = stock - 3 WHERE id = 1 AND stock >= 3` — aynı atomiklik, ama MongoDB `SELECT` adımını gerektirmeden daha kısa yol.

**Q: `$unwind` ne işe yarar? Ne zaman zorunlu?**
A: `$unwind`: Array alanını "açar" — her array elemanı için ayrı döküman üretir. `{tags: ["a","b","c"]}` → `{tag:"a"}`, `{tag:"b"}`, `{tag:"c"}` (üç ayrı döküman). Ne zaman zorunlu: Array içindeki elemanlara `$group`, `$match`, `$count` uygulamak istediğinde. Örnek: Hangi tag kaç üründe geçiyor? → Önce `$unwind(tags)`, sonra `$group(_id: "$tags").count()`. İmkânsız alternatif: SQL tek sorgu ile JSON array elemanını gruplayamaz; MongoDB'nin array desteği burada kazandırır. Dikkat: `$unwind` belge sayısını artırır (N eleman → N döküman) — erken `$match` ile az belgeye uy, sonra unwind et.

**Q: Aggregation pipeline'da stage sırası neden önemli?**
A: Pipeline = her stage önceki stage'in çıktısını alır. `$match` ilk stage'de olursa index kullanır → az veri ilerler → hız. `$match` sonra gelirse index kullanamaz → tam collection scan. Kural: `$match` ve `$project` (alan azaltma) her zaman mümkün olan en erken stage'e taşı. Örnek: `getProductsWithAvgRating()` — önce `reviews exists` match'i (erken eleme), sonra `$unwind` (pahalı), sonra `$group`, sonra `$match (HAVING)`. Tersine yazılırsa tüm collection unwind edilir. Post-aggregation `$match` = SQL'deki `HAVING` (aggregate sonrası filtre).

**Q: `$bucket` ile `$bucketAuto` farkı nedir?**
A: `$bucket`: Sınırları sen tanımlarsın — `withBoundaries(0, 10K, 25K, 50K, 100K)`. Her bucket aralığı belirsiz boyutta, anlamlı iş sınırları. `withDefaultBucket("premium")`: tanımlı sınır dışındaki değerler (>100K) özel bucket'a gider. `$bucketAuto`: MongoDB otomatik N eşit sayıda bucket oluşturur, verinin dağılımına göre sınırları belirler. Kullanım: `$bucket` bilinen kategoriler için (fiyat bantları, yaş grupları), `$bucketAuto` keşifsel analiz için. SQL karşılığı: `CASE WHEN price < 10000 THEN 'low' WHEN price < 25000 THEN 'mid' ...` — çok daha uzun.

**Q: MongoDB index türleri nelerdir? Aggregation'da index nasıl kullanılır?**
A: Single field index (`@Indexed`): Tek alan, sorgu ve sıralama için. Compound index (`@CompoundIndex`): Birden fazla alan — `{category: 1, price: -1}` hem `category` filtresinde hem `price` sıralamasında kullanılır. Text index (`@TextIndexed`): Full-text search — `$text: {$search: "laptop"}`. Aggregation'da index: Pipeline'ın ilk `$match` stage'i index kullanabilir — ESS rule (Equality, Sort, Range). `$group` ve `$unwind` sonrası index kullanımı kalmaz, bunlar in-memory çalışır. `auto-index-creation: true` — uygulama başlarken annotation'lardan index oluşturur (production'da false yap, migration ile yönet).

**Q: MongoDB ACID garantileri nedir? PostgreSQL'den farkı?**
A: MongoDB 4.0+ multi-document ACID transaction destekler. Ama temel tasarım: **single-document atomicity** — bir belge üzerindeki tüm operasyonlar atomik (embed sayesinde join'siz transaction). PostgreSQL: row-level locking, full ACID, foreign key constraint. MongoDB: document-level locking, no FK enforcement. Multi-document transaction: `session.startTransaction()` → `session.commitTransaction()` — mümkün ama performans maliyeti yüksek; mimarinin belge modeli bunu gerektirmeyecek şekilde tasarlanmalı. Tavsiye: Atomik kalması gereken verileri embed et, transaction ihtiyacını azalt.

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
