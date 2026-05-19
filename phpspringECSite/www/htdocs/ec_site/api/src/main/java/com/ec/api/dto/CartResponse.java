package com.ec.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CartResponse {
    private Long cartId;
    private Long productId;
    private String productName;
    private Integer price;
    private Integer productQty;
    private String imageName;
    private Integer stockQty;
}
