package com.ec.api.controller;

import com.ec.api.dto.OrderResponse;
import com.ec.api.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/order")
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestParam(required = false) String couponCode,
            Authentication auth) {
        return ResponseEntity.ok(orderService.placeOrder(auth.getName(), couponCode));
    }

    @GetMapping("/order")
    public List<OrderResponse> getOrders(Authentication auth) {
        return orderService.getOrders(auth.getName());
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @PutMapping("/admin/orders/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse updateOrderStatus(@PathVariable Long id, @RequestParam String status) {
        return orderService.updateOrderStatus(id, status);
    }
}
