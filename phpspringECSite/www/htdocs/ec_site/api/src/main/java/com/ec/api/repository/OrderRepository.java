package com.ec.api.repository;

import com.ec.api.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreateDateDesc(Long userId);
    List<Order> findAllByOrderByOrderIdDesc();

    @Query(value = "SELECT EXTRACT(YEAR FROM create_date)::int   AS year, " +
                   "       EXTRACT(MONTH FROM create_date)::int  AS month, " +
                   "       COUNT(*)                              AS order_count, " +
                   "       COALESCE(SUM(total_price), 0)         AS total_sales " +
                   "FROM order_table " +
                   "WHERE create_date >= :startDate " +
                   "GROUP BY EXTRACT(YEAR FROM create_date), EXTRACT(MONTH FROM create_date) " +
                   "ORDER BY year, month",
           nativeQuery = true)
    List<Object[]> findMonthlySales(@Param("startDate") LocalDate startDate);
}
