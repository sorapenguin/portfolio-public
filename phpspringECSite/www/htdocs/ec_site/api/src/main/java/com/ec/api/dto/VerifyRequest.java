package com.ec.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyRequest {
    @NotBlank
    private String sessionKey;
    @NotBlank
    private String code;
}
