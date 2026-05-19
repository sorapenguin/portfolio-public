package com.ec.api.service;

import com.ec.api.dto.DashboardResponse;
import com.ec.api.dto.MonthlySalesDto;
import com.ec.api.dto.OutOfStockDto;
import com.ec.api.dto.TopProductDto;
import com.ec.api.repository.OrderItemRepository;
import com.ec.api.repository.OrderRepository;
import com.ec.api.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public DashboardService(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    public DashboardResponse getDashboard() {
        LocalDate startDate = LocalDate.now().minusMonths(11).withDayOfMonth(1);

        List<MonthlySalesDto> monthlySales = orderRepository.findMonthlySales(startDate).stream()
                .map(r -> new MonthlySalesDto(
                        ((Number) r[0]).intValue(),
                        ((Number) r[1]).intValue(),
                        ((Number) r[2]).longValue(),
                        ((Number) r[3]).longValue()))
                .toList();

        List<TopProductDto> topProducts = orderItemRepository.findTopProducts(10).stream()
                .map(r -> new TopProductDto(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        ((Number) r[3]).longValue()))
                .toList();

        List<OutOfStockDto> outOfStock = productRepository.findOutOfStockProducts().stream()
                .map(r -> new OutOfStockDto(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        (String) r[2]))
                .toList();

        return new DashboardResponse(monthlySales, topProducts, outOfStock);
    }
}
