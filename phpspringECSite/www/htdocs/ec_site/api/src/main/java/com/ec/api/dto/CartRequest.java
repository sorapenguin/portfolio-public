package com.ec.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartRequest {
    @NotNull
    private Long productId;
}
