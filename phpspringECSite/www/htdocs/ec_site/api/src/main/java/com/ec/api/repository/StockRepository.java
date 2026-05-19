package com.ec.api.repository;

import com.ec.api.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByProductProductId(Long productId);
}
