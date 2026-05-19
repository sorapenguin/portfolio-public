package com.ec.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String status;     // "SUCCESS" or "2FA_REQUIRED"
    private String token;      // JWT (null when 2FA_REQUIRED)
    private String role;       // "USER" or "ADMIN" (null when 2FA_REQUIRED)
    private String sessionKey; // 2FA session key (null when SUCCESS)
}
