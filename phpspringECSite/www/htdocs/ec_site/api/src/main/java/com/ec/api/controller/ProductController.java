package com.ec.api.controller;

import com.ec.api.dto.ProductPageResponse;
import com.ec.api.dto.ProductRequest;
import com.ec.api.dto.ProductResponse;
import com.ec.api.dto.ProductUpdateRequest;
import com.ec.api.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public ProductPageResponse searchPublicProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer priceMin,
            @RequestParam(required = false) Integer priceMax,
            @RequestParam(defaultValue = "0") int inStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return productService.searchPublicProducts(keyword, categoryId, priceMin, priceMax, inStockOnly, page, size);
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @GetMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    @PostMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(req));
    }

    @PutMapping("/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateProduct(@PathVariable Long id,
                                         @Valid @RequestBody ProductUpdateRequest req) {
        return productService.updateProduct(id, req);
    }

    @PutMapping("/admin/products/{id}/toggle-public")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse togglePublic(@PathVariable Long id, @RequestParam int publicFlg) {
        return productService.togglePublic(id, publicFlg);
    }

    @PutMapping("/admin/products/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse updateStock(@PathVariable Long id, @RequestParam int stockQty) {
        return productService.updateStock(id, stockQty);
    }

    @DeleteMapping("/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
