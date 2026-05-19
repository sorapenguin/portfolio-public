package com.ec.api.repository;

import com.ec.api.entity.ProductHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductHistoryRepository extends JpaRepository<ProductHistory, Long> {

    List<ProductHistory> findByProductIdOrderByChangedAtDesc(Long productId);
}
