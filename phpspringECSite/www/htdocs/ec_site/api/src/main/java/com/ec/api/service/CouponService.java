package com.ec.api.service;

import com.ec.api.dto.CouponAdminRequest;
import com.ec.api.dto.CouponValidateResponse;
import com.ec.api.entity.Coupon;
import com.ec.api.repository.CouponRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public CouponValidateResponse validate(String code) {
        Coupon coupon = findValidCoupon(code);
        return new CouponValidateResponse(coupon.getCode(), coupon.getDiscountRate(),
                coupon.getDiscountRate() + "% 割引が適用されます");
    }

    public Coupon findValidCoupon(String code) {
        Coupon coupon = couponRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "クーポンが無効です"));
        if (Boolean.TRUE.equals(coupon.getIsUsed()) || coupon.getExpiresAt().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "クーポンが無効です");
        }
        return coupon;
    }

    public Coupon createCoupon(CouponAdminRequest req) {
        if (couponRepository.findByCode(req.getCode().toUpperCase()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同じコードのクーポンが既に存在します");
        }
        if (req.getDiscountRate() == null || req.getDiscountRate() < 1 || req.getDiscountRate() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "割引率は1〜100の整数で入力してください");
        }
        Coupon coupon = new Coupon();
        coupon.setCode(req.getCode().toUpperCase());
        coupon.setDiscountRate(req.getDiscountRate());
        coupon.setExpiresAt(LocalDate.parse(req.getExpiresAt()));
        coupon.setIsUsed(false);
        coupon.setCreateDate(LocalDate.now());
        coupon.setUpdateDate(LocalDate.now());
        return couponRepository.save(coupon);
    }

    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }
}
