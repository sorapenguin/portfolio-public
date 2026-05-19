package com.ec.api.dto;

import com.ec.api.entity.Product;
import lombok.Data;

@Data
public class FavoriteResponse {
    private Long favoriteId;
    private Long productId;
    private String productName;
    private Integer price;
    private Integer stockQty;
    private String imageName;
    private String categoryName;

    public static FavoriteResponse from(Long favoriteId, Product p) {
        FavoriteResponse r = new FavoriteResponse();
        r.setFavoriteId(favoriteId);
        r.setProductId(p.getProductId());
        r.setProductName(p.getProductName());
        r.setPrice(p.getPrice());
        r.setStockQty(p.getStock() != null ? p.getStock().getStockQty() : 0);
        r.setImageName(p.getImage() != null ? p.getImage().getImageName() : null);
        r.setCategoryName(p.getCategory() != null ? p.getCategory().getCategoryName() : null);
        return r;
    }
}
