package com.ec.api.service;

import com.ec.api.dto.OrderResponse;
import com.ec.api.entity.*;
import com.ec.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock StockRepository stockRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock CouponService couponService;

    @InjectMocks OrderService orderService;

    private User user;
    private Product product;
    private Stock stock;
    private Cart cartItem;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(1L);
        user.setUserName("testuser");

        stock = new Stock();
        stock.setStockId(1L);
        stock.setStockQty(10);
        stock.setUpdateDate(LocalDate.now());

        product = new Product();
        product.setProductId(1L);
        product.setProductName("テスト商品");
        product.setPrice(1000);
        product.setStock(stock);

        cartItem = new Cart();
        cartItem.setCartId(1L);
        cartItem.setUserId(1L);
        cartItem.setProductId(1L);
        cartItem.setProductQty(2);
    }

    private Order savedOrder(int totalPrice) {
        Order o = new Order();
        o.setOrderId(100L);
        o.setUserId(1L);
        o.setTotalPrice(totalPrice);
        o.setStatus("未処理");
        o.setCreateDate(LocalDate.now());
        return o;
    }

    // --- placeOrder ---

    @Test
    void placeOrder_noDiscount_totalEqualsSubtotal() {
        when(userRepository.findByUserName("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cartItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any())).thenReturn(savedOrder(2000));

        OrderResponse res = orderService.placeOrder("testuser", null);

        assertThat(res.getTotalPrice()).isEqualTo(2000);
        assertThat(res.getStatus()).isEqualTo("未処理");
        assertThat(res.getDiscountRate()).isNull();
        assertThat(res.getDiscountAmount()).isNull();
        verify(stockRepository).save(any(Stock.class));
        verify(cartRepository).deleteAllByUserId(1L);
    }

    @Test
    void placeOrder_withCoupon_appliesDiscount() {
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE20");
        coupon.setDiscountRate(20);
        coupon.setIsUsed(false);
        coupon.setExpiresAt(LocalDate.now().plusDays(7));

        when(userRepository.findByUserName("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cartItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(couponService.findValidCoupon("SAVE20")).thenReturn(coupon);
        when(orderRepository.save(any())).thenReturn(savedOrder(1600));

        OrderResponse res = orderService.placeOrder("testuser", "SAVE20");

        // subtotal=2000, discount=20% → discountAmount=400, total=1600
        assertThat(res.getDiscountRate()).isEqualTo(20);
        assertThat(res.getDiscountAmount()).isEqualTo(400);
        assertThat(coupon.getIsUsed()).isTrue();
        verify(couponService).findValidCoupon("SAVE20");
    }

    @Test
    void placeOrder_couponCode_passedAsIsToService() {
        // uppercase変換はCouponService.findValidCoupon()内部が責務
        // OrderServiceはそのまま渡すだけであることを確認
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE10");
        coupon.setDiscountRate(10);
        coupon.setIsUsed(false);
        coupon.setExpiresAt(LocalDate.now().plusDays(7));

        when(userRepository.findByUserName("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cartItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(couponService.findValidCoupon("save10")).thenReturn(coupon);
        when(orderRepository.save(any())).thenReturn(savedOrder(1800));

        orderService.placeOrder("testuser", "save10");

        verify(couponService).findValidCoupon("save10");
    }

    @Test
    void placeOrder_stockDecreasedByCartQuantity() {
        when(userRepository.findByUserName("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cartItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any())).thenReturn(savedOrder(2000));

        orderService.placeOrder("testuser", null);

        // stock was 10, cart qty=2 → should be 8
        assertThat(stock.getStockQty()).isEqualTo(8);
    }

    @Test
    void placeOrder_emptyCart_throwsBadRequest() {
        when(userRepository.findByUserName("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.placeOrder("testuser", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("カートが空です");
    }

    @Test
    void placeOrder_insufficientStock_throwsBadRequest() {
        stock.setStockQty(1); // cartItem.qty=2 なので不足

        when(userRepository.findByUserName("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cartItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.placeOrder("testuser", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("在庫が不足しています");
    }

    @Test
    void placeOrder_userNotFound_throwsNotFound() {
        when(userRepository.findByUserName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder("unknown", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ユーザーが見つかりません");
    }

    // --- updateOrderStatus ---

    @Test
    void updateOrderStatus_validStatus_updatesOrder() {
        Order order = new Order();
        order.setOrderId(1L);
        order.setUserId(1L);
        order.setTotalPrice(2000);
        order.setStatus("未処理");
        order.setCreateDate(LocalDate.now());

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        OrderResponse res = orderService.updateOrderStatus(1L, "処理中");

        assertThat(res.getStatus()).isEqualTo("処理中");
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrderStatus_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> orderService.updateOrderStatus(1L, "不正なステータス"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("無効なステータスです");
    }

    @Test
    void updateOrderStatus_orderNotFound_throwsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(99L, "処理中"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("注文が見つかりません");
    }
}
