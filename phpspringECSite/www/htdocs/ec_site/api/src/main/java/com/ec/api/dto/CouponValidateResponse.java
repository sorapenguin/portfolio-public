package com.ec.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CouponValidateResponse {
    private String code;
    private Integer discountRate;
    private String message;
}
