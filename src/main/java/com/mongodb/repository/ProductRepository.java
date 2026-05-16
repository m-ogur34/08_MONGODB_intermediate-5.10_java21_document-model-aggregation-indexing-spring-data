package com.mongodb.repository;

import com.mongodb.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

/**
 * Spring Data MongoDB Repository
 * JPA Repository ile aynı mantık — farklı implementasyon
 */
public interface ProductRepository extends MongoRepository<Product, String> {

    // Method naming convention
    List<Product> findByCategory(String category);
    List<Product> findByCategoryAndActiveTrue(String category);
    List<Product> findByPriceBetween(double min, double max);
    List<Product> findByTagsContaining(String tag);
    List<Product> findByStockGreaterThan(int minStock);

    // Pagination
    Page<Product> findByCategory(String category, Pageable pageable);

    // Custom @Query
    @Query("{ 'category': ?0, 'price': { $lte: ?1 }, 'active': true }")
    List<Product> findAffordableByCategory(String category, double maxPrice);

    @Query("{ 'specifications.brand': ?0 }")
    List<Product> findByBrand(String brand);

    @Query("{ $text: { $search: ?0 } }")
    List<Product> fullTextSearch(String searchTerm);

    // Aggregation ile ortalama fiyat
    @Query(value = "{ 'category': ?0 }", fields = "{ 'name': 1, 'price': 1 }")
    List<Product> findNameAndPriceByCategory(String category);

    long countByCategoryAndActiveTrue(String category);

    boolean existsByNameAndCategory(String name, String category);
}
