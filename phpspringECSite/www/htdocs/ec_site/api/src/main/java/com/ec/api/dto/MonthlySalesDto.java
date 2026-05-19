package com.ec.api.dto;

import lombok.Data;

@Data
public class MonthlySalesDto {
    private int year;
    private int month;
    private long orderCount;
    private long totalSales;

    public MonthlySalesDto(int year, int month, long orderCount, long totalSales) {
        this.year = year;
        this.month = month;
        this.orderCount = orderCount;
        this.totalSales = totalSales;
    }
}
