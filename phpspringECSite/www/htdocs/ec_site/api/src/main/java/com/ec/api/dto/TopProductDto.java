package com.ec.api.dto;

import lombok.Data;

@Data
public class TopProductDto {
    private Long productId;
    private String productName;
    private long totalQty;
    private long totalSales;

    public TopProductDto(Long productId, String productName, long totalQty, long totalSales) {
        this.productId = productId;
        this.productName = productName;
        this.totalQty = totalQty;
        this.totalSales = totalSales;
    }
}
