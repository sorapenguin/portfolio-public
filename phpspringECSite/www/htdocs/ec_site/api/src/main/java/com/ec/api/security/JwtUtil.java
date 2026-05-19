package com.ec.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key())
                .compact();
    }

    public String username(String token) {
        return claims(token).getSubject();
    }

    public String role(String token) {
        return claims(token).get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            claims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationMs(String token) {
        return claims(token).getExpiration().getTime();
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
    }
}
