package com.ec.api.service;

import com.ec.api.dto.ProductResponse;
import com.ec.api.entity.Product;
import com.ec.api.entity.Stock;
import com.ec.api.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock StockRepository stockRepository;
    @Mock ProductImageRepository imageRepository;
    @Mock ProductHistoryRepository historyRepository;
    @Mock CategoryRepository categoryRepository;

    @InjectMocks ProductService productService;

    private Product product(Long id, int deletedFlg, int stockQty) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductName("商品" + id);
        p.setPrice(500);
        p.setPublicFlg(1);
        p.setDeletedFlg(deletedFlg);
        p.setCreateDate(LocalDate.now());
        p.setUpdateDate(LocalDate.now());

        Stock s = new Stock();
        s.setStockId(id);
        s.setStockQty(stockQty);
        s.setCreateDate(LocalDate.now());
        s.setUpdateDate(LocalDate.now());
        p.setStock(s);

        return p;
    }

    // --- getProduct ---

    @Test
    void getProduct_exists_returnsResponse() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, 0, 5)));

        ProductResponse res = productService.getProduct(1L);

        assertThat(res.getProductId()).isEqualTo(1L);
        assertThat(res.getProductName()).isEqualTo("商品1");
        assertThat(res.getStockQty()).isEqualTo(5);
    }

    @Test
    void getProduct_notFound_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("商品が見つかりません");
    }

    @Test
    void getProduct_softDeleted_throwsNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, 1, 5)));

        assertThatThrownBy(() -> productService.getProduct(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("商品が見つかりません");
    }

    // --- deleteProduct ---

    @Test
    void deleteProduct_setsDeletedFlgToOne() {
        Product p = product(1L, 0, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        productService.deleteProduct(1L);

        assertThat(p.getDeletedFlg()).isEqualTo(1);
        verify(productRepository).save(p);
        verify(historyRepository).save(any());
    }

    @Test
    void deleteProduct_alreadyDeleted_throwsNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, 1, 0)));

        assertThatThrownBy(() -> productService.deleteProduct(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("商品が見つかりません");

        verify(productRepository, never()).save(any());
    }

    // --- updateStock ---

    @Test
    void updateStock_setsNewQuantity() {
        Product p = product(1L, 0, 5);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        productService.updateStock(1L, 99);

        assertThat(p.getStock().getStockQty()).isEqualTo(99);
        verify(stockRepository).save(p.getStock());
        verify(historyRepository).save(any());
    }

    @Test
    void updateStock_stockIsNull_createsNewStock() {
        Product p = product(1L, 0, 0);
        p.setStock(null); // 在庫レコードなし
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        productService.updateStock(1L, 50);

        verify(stockRepository).save(any(Stock.class));
    }

    // --- togglePublic ---

    @Test
    void togglePublic_setsPublicFlg() {
        Product p = product(1L, 0, 5);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        productService.togglePublic(1L, 0);

        assertThat(p.getPublicFlg()).isEqualTo(0);
        verify(productRepository).save(p);
    }
}
