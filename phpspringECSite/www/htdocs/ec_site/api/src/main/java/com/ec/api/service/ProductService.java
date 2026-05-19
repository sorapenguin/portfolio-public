package com.ec.api.service;

import com.ec.api.dto.ProductPageResponse;
import com.ec.api.dto.ProductRequest;
import com.ec.api.dto.ProductResponse;
import com.ec.api.dto.ProductUpdateRequest;
import com.ec.api.entity.Category;
import com.ec.api.entity.Product;
import com.ec.api.entity.ProductHistory;
import com.ec.api.entity.ProductImage;
import com.ec.api.entity.Stock;
import com.ec.api.repository.CategoryRepository;
import com.ec.api.repository.ProductHistoryRepository;
import com.ec.api.repository.ProductImageRepository;
import com.ec.api.repository.ProductRepository;
import com.ec.api.repository.StockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ProductImageRepository imageRepository;
    private final ProductHistoryRepository historyRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          StockRepository stockRepository,
                          ProductImageRepository imageRepository,
                          ProductHistoryRepository historyRepository,
                          CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.imageRepository = imageRepository;
        this.historyRepository = historyRepository;
        this.categoryRepository = categoryRepository;
    }

    public ProductPageResponse searchPublicProducts(String keyword, Long categoryId, Integer priceMin, Integer priceMax, int inStockOnly, int page, int size) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> pageResult = productRepository.searchPublicProducts(kw, categoryId, priceMin, priceMax, inStockOnly, pageable);

        ProductPageResponse res = new ProductPageResponse();
        res.setProducts(pageResult.getContent().stream().map(ProductResponse::from).toList());
        res.setTotalElements(pageResult.getTotalElements());
        res.setTotalPages(pageResult.getTotalPages());
        res.setPage(page);
        res.setSize(size);
        return res;
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findByDeletedFlg(0).stream()
                .map(ProductResponse::from).toList();
    }

    public ProductResponse getProduct(Long id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest req) {
        Product p = new Product();
        p.setProductName(req.getProductName());
        p.setPrice(req.getPrice());
        p.setPublicFlg(req.getPublicFlg());
        p.setDescription(req.getDescription());
        p.setCategory(resolveCategory(req.getCategoryId()));
        p.setDeletedFlg(0);
        p.setCreateDate(LocalDate.now());
        p.setUpdateDate(LocalDate.now());
        p = productRepository.save(p);

        Stock stock = new Stock();
        stock.setProduct(p);
        stock.setStockQty(req.getStockQty());
        stock.setCreateDate(LocalDate.now());
        stock.setUpdateDate(LocalDate.now());
        stockRepository.save(stock);

        ProductImage img = new ProductImage();
        img.setProduct(p);
        img.setImageName(req.getImageName());
        img.setCreateDate(LocalDate.now());
        img.setUpdateDate(LocalDate.now());
        imageRepository.save(img);

        p = productRepository.findById(p.getProductId()).orElseThrow();
        saveHistory(p, "CREATE");
        return ProductResponse.from(p);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest req) {
        Product p = findOrThrow(id);
        p.setPrice(req.getPrice());
        p.setDescription(req.getDescription());
        p.setCategory(resolveCategory(req.getCategoryId()));
        p.setUpdateDate(LocalDate.now());
        productRepository.save(p);

        Stock stock = p.getStock();
        if (stock == null) {
            stock = new Stock();
            stock.setProduct(p);
            stock.setCreateDate(LocalDate.now());
        }
        stock.setStockQty(req.getStockQty());
        stock.setUpdateDate(LocalDate.now());
        stockRepository.save(stock);

        p = productRepository.findById(id).orElseThrow();
        saveHistory(p, "UPDATE");
        return ProductResponse.from(p);
    }

    @Transactional
    public ProductResponse togglePublic(Long id, int publicFlg) {
        Product p = findOrThrow(id);
        p.setPublicFlg(publicFlg);
        p.setUpdateDate(LocalDate.now());
        productRepository.save(p);
        p = productRepository.findById(id).orElseThrow();
        saveHistory(p, "UPDATE");
        return ProductResponse.from(p);
    }

    @Transactional
    public ProductResponse updateStock(Long id, int stockQty) {
        Product p = findOrThrow(id);
        Stock stock = p.getStock();
        if (stock == null) {
            stock = new Stock();
            stock.setProduct(p);
            stock.setCreateDate(LocalDate.now());
        }
        stock.setStockQty(stockQty);
        stock.setUpdateDate(LocalDate.now());
        stockRepository.save(stock);
        p = productRepository.findById(id).orElseThrow();
        saveHistory(p, "UPDATE");
        return ProductResponse.from(p);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product p = findOrThrow(id);
        p.setDeletedFlg(1);
        p.setUpdateDate(LocalDate.now());
        productRepository.save(p);
        saveHistory(p, "DELETE");
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定されたカテゴリが存在しません"));
    }

    private void saveHistory(Product p, String operation) {
        ProductHistory h = new ProductHistory();
        h.setProductId(p.getProductId());
        h.setOperation(operation);
        h.setChangedAt(LocalDateTime.now());
        h.setProductName(p.getProductName());
        h.setPrice(p.getPrice());
        h.setDescription(p.getDescription());
        h.setCategory(p.getCategory() != null ? p.getCategory().getCategoryName() : null);
        h.setStockQty(p.getStock() != null ? p.getStock().getStockQty() : null);
        h.setPublicFlg(p.getPublicFlg());
        h.setDeletedFlg(p.getDeletedFlg());
        historyRepository.save(h);
    }

    private Product findOrThrow(Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));
        if (Integer.valueOf(1).equals(p.getDeletedFlg())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません");
        }
        return p;
    }
}
