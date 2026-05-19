package com.ec.api.controller;

import com.ec.api.dto.CouponAdminRequest;
import com.ec.api.dto.CouponValidateResponse;
import com.ec.api.entity.Coupon;
import com.ec.api.service.CouponService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/coupon/validate")
    public CouponValidateResponse validate(@RequestParam String code) {
        return couponService.validate(code);
    }

    @GetMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Coupon> getAllCoupons() {
        return couponService.getAllCoupons();
    }

    @PostMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Coupon> createCoupon(@RequestBody CouponAdminRequest req) {
        return ResponseEntity.ok(couponService.createCoupon(req));
    }
}
