package com.ec.api.repository;

import com.ec.api.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    // F: N+1解消用 — 複数 orderId の明細を1クエリで一括取得
    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId IN :orderIds")
    List<OrderItem> findByOrderIdIn(@Param("orderIds") List<Long> orderIds);

    @Query(value = "SELECT product_id, " +
                   "       product_name, " +
                   "       SUM(qty)         AS total_qty, " +
                   "       SUM(price * qty) AS total_sales " +
                   "FROM order_item_table " +
                   "GROUP BY product_id, product_name " +
                   "ORDER BY total_qty DESC " +
                   "LIMIT :lim",
           nativeQuery = true)
    List<Object[]> findTopProducts(@Param("lim") int limit);
}
