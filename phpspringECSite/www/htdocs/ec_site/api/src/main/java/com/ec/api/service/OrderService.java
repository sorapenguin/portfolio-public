package com.ec.api.service;

import com.ec.api.dto.OrderResponse;
import com.ec.api.dto.OrderResponse.OrderItemResponse;
import com.ec.api.entity.*;
import com.ec.api.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final CouponService couponService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CartRepository cartRepository,
                        UserRepository userRepository,
                        ProductRepository productRepository,
                        StockRepository stockRepository,
                        CouponService couponService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.couponService = couponService;
    }

    @Transactional
    public OrderResponse placeOrder(String username, String couponCode) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

        List<Cart> cartItems = cartRepository.findByUserId(user.getUserId());
        if (cartItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "カートが空です");
        }

        // 在庫チェックと小計集計
        int subtotal = 0;
        for (Cart item : cartItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));
            int stockQty = product.getStock() != null ? product.getStock().getStockQty() : 0;
            if (stockQty < item.getProductQty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        product.getProductName() + " の在庫が不足しています（残り " + stockQty + " 個）");
            }
            subtotal += product.getPrice() * item.getProductQty();
        }

        // クーポン適用（同一トランザクション内で使用済みに更新）
        Integer discountRate = null;
        Integer discountAmount = null;
        int totalPrice = subtotal;

        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = couponService.findValidCoupon(couponCode);
            discountRate = coupon.getDiscountRate();
            discountAmount = subtotal * discountRate / 100;
            totalPrice = subtotal - discountAmount;
            coupon.setIsUsed(true);
            coupon.setUpdateDate(LocalDate.now());
        }

        // 注文作成
        Order order = new Order();
        order.setUserId(user.getUserId());
        order.setTotalPrice(totalPrice);
        order.setStatus("未処理");
        order.setCreateDate(LocalDate.now());
        order.setUpdateDate(LocalDate.now());
        order = orderRepository.save(order);

        List<OrderItemResponse> responseItems = new ArrayList<>();

        for (Cart item : cartItems) {
            Product product = productRepository.findById(item.getProductId()).orElseThrow();

            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getOrderId());
            oi.setProductId(item.getProductId());
            oi.setProductName(product.getProductName());
            oi.setPrice(product.getPrice());
            oi.setQty(item.getProductQty());
            oi.setImageName(product.getImage() != null ? product.getImage().getImageName() : null);
            oi.setCreateDate(LocalDate.now());
            oi.setUpdateDate(LocalDate.now());
            orderItemRepository.save(oi);

            // 在庫を減らす
            Stock stock = product.getStock();
            stock.setStockQty(stock.getStockQty() - item.getProductQty());
            stock.setUpdateDate(LocalDate.now());
            stockRepository.save(stock);

            responseItems.add(new OrderItemResponse(
                    product.getProductName(), product.getPrice(),
                    item.getProductQty(), oi.getImageName()
            ));
        }

        // カートをクリア
        cartRepository.deleteAllByUserId(user.getUserId());

        return new OrderResponse(order.getOrderId(), totalPrice, "未処理",
                order.getCreateDate().toString(), responseItems, null, discountRate, discountAmount);
    }

    public List<OrderResponse> getOrders(String username) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));

        List<Order> orders = orderRepository.findByUserIdOrderByCreateDateDesc(user.getUserId());
        // F: N+1解消 — 全明細を1クエリで取得してメモリ上でグループ化
        Map<Long, List<OrderItem>> itemsByOrder = fetchItemsGrouped(orders);

        return orders.stream().map(order -> {
            List<OrderItemResponse> items = itemsByOrder.getOrDefault(order.getOrderId(), List.of()).stream()
                    .map(oi -> new OrderItemResponse(oi.getProductName(), oi.getPrice(), oi.getQty(), oi.getImageName()))
                    .toList();
            return new OrderResponse(order.getOrderId(), order.getTotalPrice(), order.getStatus(),
                    order.getCreateDate() != null ? order.getCreateDate().toString() : null,
                    items, null, null, null);
        }).toList();
    }

    public List<OrderResponse> getAllOrders() {
        Map<Long, String> userMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getUserId, User::getUserName));

        List<Order> orders = orderRepository.findAllByOrderByOrderIdDesc();
        // F: N+1解消 — 全明細を1クエリで取得してメモリ上でグループ化
        Map<Long, List<OrderItem>> itemsByOrder = fetchItemsGrouped(orders);

        return orders.stream().map(order -> {
            String userName = userMap.getOrDefault(order.getUserId(), "不明");
            List<OrderItemResponse> items = itemsByOrder.getOrDefault(order.getOrderId(), List.of()).stream()
                    .map(oi -> new OrderItemResponse(oi.getProductName(), oi.getPrice(), oi.getQty(), oi.getImageName()))
                    .toList();
            return new OrderResponse(order.getOrderId(), order.getTotalPrice(), order.getStatus(),
                    order.getCreateDate() != null ? order.getCreateDate().toString() : null,
                    items, userName, null, null);
        }).toList();
    }

    /** 注文リストの全明細を IN 句で一括取得し orderId でグループ化する（N+1対策） */
    private Map<Long, List<OrderItem>> fetchItemsGrouped(List<Order> orders) {
        if (orders.isEmpty()) return Map.of();
        List<Long> ids = orders.stream().map(Order::getOrderId).toList();
        return orderItemRepository.findByOrderIdIn(ids).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        List<String> validStatuses = List.of("未処理", "処理中", "発送済み", "完了");
        if (!validStatuses.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "無効なステータスです");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "注文が見つかりません"));
        order.setStatus(status);
        order.setUpdateDate(LocalDate.now());
        orderRepository.save(order);

        String userName = userRepository.findById(order.getUserId())
                .map(User::getUserName).orElse("不明");
        List<OrderItemResponse> items = orderItemRepository.findByOrderId(orderId).stream()
                .map(oi -> new OrderItemResponse(oi.getProductName(), oi.getPrice(), oi.getQty(), oi.getImageName()))
                .toList();
        return new OrderResponse(orderId, order.getTotalPrice(), order.getStatus(),
                order.getCreateDate() != null ? order.getCreateDate().toString() : null,
                items, userName, null, null);
    }
}
