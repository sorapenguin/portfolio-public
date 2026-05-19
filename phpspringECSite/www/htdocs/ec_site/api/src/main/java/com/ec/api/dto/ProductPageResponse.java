package com.ec.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductPageResponse {
    private List<ProductResponse> products;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
