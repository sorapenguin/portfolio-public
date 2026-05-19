package com.ec.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductUpdateRequest {
    @NotNull
    @Min(0)
    private Integer price;

    @NotNull
    @Min(0)
    private Integer stockQty;

    private String description;

    private Long categoryId;
}
