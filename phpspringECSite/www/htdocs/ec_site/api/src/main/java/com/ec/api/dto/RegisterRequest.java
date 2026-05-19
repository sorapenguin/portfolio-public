package com.ec.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String userName;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;
}
