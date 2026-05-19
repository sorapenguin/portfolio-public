package com.ec.api.dto;

import com.ec.api.entity.Product;
import lombok.Data;

@Data
public class ProductResponse {
    private Long productId;
    private String productName;
    private Integer price;
    private Integer publicFlg;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Integer stockQty;
    private String imageName;

    public static ProductResponse from(Product p) {
        ProductResponse r = new ProductResponse();
        r.setProductId(p.getProductId());
        r.setProductName(p.getProductName());
        r.setPrice(p.getPrice());
        r.setPublicFlg(p.getPublicFlg());
        r.setDescription(p.getDescription());
        r.setCategoryId(p.getCategory() != null ? p.getCategory().getCategoryId() : null);
        r.setCategoryName(p.getCategory() != null ? p.getCategory().getCategoryName() : null);
        r.setStockQty(p.getStock() != null ? p.getStock().getStockQty() : 0);
        r.setImageName(p.getImage() != null ? p.getImage().getImageName() : null);
        return r;
    }
}
