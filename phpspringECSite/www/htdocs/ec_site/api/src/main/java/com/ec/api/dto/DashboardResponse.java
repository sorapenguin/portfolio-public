package com.ec.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class DashboardResponse {
    private List<MonthlySalesDto> monthlySales;
    private List<TopProductDto> topProducts;
    private List<OutOfStockDto> outOfStock;

    public DashboardResponse(List<MonthlySalesDto> monthlySales,
                              List<TopProductDto> topProducts,
                              List<OutOfStockDto> outOfStock) {
        this.monthlySales = monthlySales;
        this.topProducts = topProducts;
        this.outOfStock = outOfStock;
    }
}
