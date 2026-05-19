package com.ec.api.repository;

import com.ec.api.entity.Category;
import com.ec.api.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByDeletedFlg(int deletedFlg);

    long countByCategory(Category category);

    @Query(value = "SELECT p.* FROM product_table p " +
                   "LEFT JOIN category_table c ON p.category_id = c.category_id " +
                   "WHERE p.public_flg = 1 AND p.deleted_flg = 0 " +
                   "AND (:keyword IS NULL OR LOWER(p.product_name) LIKE LOWER('%' || :keyword || '%')) " +
                   "AND (cast(:categoryId as bigint) IS NULL OR p.category_id = cast(:categoryId as bigint)) " +
                   "AND (cast(:priceMin as integer) IS NULL OR p.price >= cast(:priceMin as integer)) " +
                   "AND (cast(:priceMax as integer) IS NULL OR p.price <= cast(:priceMax as integer)) " +
                   "AND (:inStockOnly = 0 OR EXISTS (SELECT 1 FROM stock_table s WHERE s.product_id = p.product_id AND s.stock_qty > 0))",
           countQuery = "SELECT count(*) FROM product_table p " +
                        "WHERE p.public_flg = 1 AND p.deleted_flg = 0 " +
                        "AND (:keyword IS NULL OR LOWER(p.product_name) LIKE LOWER('%' || :keyword || '%')) " +
                        "AND (cast(:categoryId as bigint) IS NULL OR p.category_id = cast(:categoryId as bigint)) " +
                        "AND (cast(:priceMin as integer) IS NULL OR p.price >= cast(:priceMin as integer)) " +
                        "AND (cast(:priceMax as integer) IS NULL OR p.price <= cast(:priceMax as integer)) " +
                        "AND (:inStockOnly = 0 OR EXISTS (SELECT 1 FROM stock_table s WHERE s.product_id = p.product_id AND s.stock_qty > 0))",
           nativeQuery = true)
    Page<Product> searchPublicProducts(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("priceMin") Integer priceMin,
            @Param("priceMax") Integer priceMax,
            @Param("inStockOnly") int inStockOnly,
            Pageable pageable);

    @Query(value = "SELECT p.product_id, p.product_name, i.image_name " +
                   "FROM product_table p " +
                   "JOIN stock_table s ON s.product_id = p.product_id " +
                   "LEFT JOIN image_table i ON i.product_id = p.product_id " +
                   "WHERE s.stock_qty = 0 AND p.deleted_flg = 0 AND p.public_flg = 1 " +
                   "ORDER BY p.product_name",
           nativeQuery = true)
    List<Object[]> findOutOfStockProducts();
}
