package com.ec.api.dto;

import lombok.Data;

@Data
public class CouponAdminRequest {
    private String code;
    private Integer discountRate;
    private String expiresAt;
}
