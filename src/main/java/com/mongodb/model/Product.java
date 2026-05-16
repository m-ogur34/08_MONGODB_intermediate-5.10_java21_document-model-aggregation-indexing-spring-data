package com.mongodb.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Document Model
 *
 * Embedding vs Referencing:
 *   Embed  → Birlikte okunacak, sık değişmeyen (adres, özellikler)
 *   Ref    → Çok-çok ilişki, bağımsız yaşam döngüsü (kategori, marka)
 */
@Document(collection = "products")
@CompoundIndex(def = "{'category': 1, 'price': -1}")
public class Product {

    @Id
    private String id;

    @TextIndexed(weight = 2)
    private String name;

    @TextIndexed
    private String description;

    @Indexed
    private String category;

    private double price;
    private int stock;

    // Embedded document — kategori ağacı yerine embed
    private List<String> tags;

    // Nested object
    @Field("specs")
    private Map<String, Object> specifications;

    // Embedded array of objects
    private List<Review> reviews;

    @Indexed
    private boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Embedded document
    public record Review(String userId, int rating, String comment, LocalDateTime date) {}

    // Getters & Setters
    public String getId()                          { return id; }
    public void setId(String id)                   { this.id = id; }
    public String getName()                        { return name; }
    public void setName(String name)               { this.name = name; }
    public String getDescription()                 { return description; }
    public void setDescription(String desc)        { this.description = desc; }
    public String getCategory()                    { return category; }
    public void setCategory(String category)       { this.category = category; }
    public double getPrice()                       { return price; }
    public void setPrice(double price)             { this.price = price; }
    public int getStock()                          { return stock; }
    public void setStock(int stock)                { this.stock = stock; }
    public List<String> getTags()                  { return tags; }
    public void setTags(List<String> tags)         { this.tags = tags; }
    public Map<String, Object> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, Object> s) { this.specifications = s; }
    public List<Review> getReviews()               { return reviews; }
    public void setReviews(List<Review> reviews)   { this.reviews = reviews; }
    public boolean isActive()                      { return active; }
    public void setActive(boolean active)          { this.active = active; }
    public LocalDateTime getCreatedAt()            { return createdAt; }
}
