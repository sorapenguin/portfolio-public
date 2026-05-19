package com.ec.api.controller;

import com.ec.api.dto.LoginRequest;
import com.ec.api.dto.LoginResponse;
import com.ec.api.dto.RegisterRequest;
import com.ec.api.dto.VerifyRequest;
import com.ec.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest httpReq) {
        return ResponseEntity.ok(authService.login(req, extractIp(httpReq)));
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<LoginResponse> verify2fa(@Valid @RequestBody VerifyRequest req,
                                                   HttpServletRequest httpReq) {
        return ResponseEntity.ok(authService.verify2fa(req, extractIp(httpReq)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok().build();
    }

    /** Traefik等のリバースプロキシが付与する X-Forwarded-For を優先して返す */
    private String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
