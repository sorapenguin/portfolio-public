package com.ec.api.controller;

import com.ec.api.dto.FavoriteResponse;
import com.ec.api.service.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public List<FavoriteResponse> getFavorites(Authentication auth) {
        return favoriteService.getFavorites(auth.getName());
    }

    @GetMapping("/ids")
    public List<Long> getFavoriteIds(Authentication auth) {
        return favoriteService.getFavoriteProductIds(auth.getName());
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Map<String, Boolean>> toggle(@PathVariable Long productId, Authentication auth) {
        boolean favorited = favoriteService.toggle(auth.getName(), productId);
        return ResponseEntity.ok(Map.of("favorited", favorited));
    }
}
