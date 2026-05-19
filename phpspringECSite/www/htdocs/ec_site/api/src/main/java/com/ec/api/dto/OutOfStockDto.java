package com.ec.api.dto;

import lombok.Data;

@Data
public class OutOfStockDto {
    private Long productId;
    private String productName;
    private String imageName;

    public OutOfStockDto(Long productId, String productName, String imageName) {
        this.productId = productId;
        this.productName = productName;
        this.imageName = imageName;
    }
}
