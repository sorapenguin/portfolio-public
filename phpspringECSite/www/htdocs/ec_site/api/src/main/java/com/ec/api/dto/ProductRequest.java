package com.ec.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductRequest {
    @NotBlank
    private String productName;

    @NotNull
    @Min(0)
    private Integer price;

    @NotNull
    @Min(0)
    private Integer stockQty;

    @NotNull
    private Integer publicFlg;

    private String description;

    private Long categoryId;

    @NotBlank
    private String imageName;
}
