package com.ec.api.controller;

import com.ec.api.dto.CartRequest;
import com.ec.api.dto.CartResponse;
import com.ec.api.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public List<CartResponse> getCart(Authentication auth) {
        return cartService.getCart(auth.getName());
    }

    @PostMapping("/add")
    public ResponseEntity<Void> addToCart(@Valid @RequestBody CartRequest req, Authentication auth) {
        cartService.addToCart(auth.getName(), req);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{cartId}")
    public ResponseEntity<Void> updateQty(@PathVariable Long cartId,
                                           @RequestParam int qty,
                                           Authentication auth) {
        cartService.updateQty(auth.getName(), cartId, qty);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{cartId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long cartId, Authentication auth) {
        cartService.removeItem(auth.getName(), cartId);
        return ResponseEntity.noContent().build();
    }
}
