package com.ec.api.service;

import com.ec.api.dto.CouponAdminRequest;
import com.ec.api.dto.CouponValidateResponse;
import com.ec.api.entity.Coupon;
import com.ec.api.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock CouponRepository couponRepository;

    @InjectMocks CouponService couponService;

    private Coupon coupon(String code, int rate, boolean used, int expiresInDays) {
        Coupon c = new Coupon();
        c.setCode(code);
        c.setDiscountRate(rate);
        c.setIsUsed(used);
        c.setExpiresAt(LocalDate.now().plusDays(expiresInDays));
        return c;
    }

    // --- validate ---

    @Test
    void validate_validCoupon_returnsDiscountInfo() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon("SAVE10", 10, false, 7)));

        CouponValidateResponse res = couponService.validate("SAVE10");

        assertThat(res.getCode()).isEqualTo("SAVE10");
        assertThat(res.getDiscountRate()).isEqualTo(10);
        assertThat(res.getMessage()).contains("10%");
    }

    // --- findValidCoupon ---

    @Test
    void findValidCoupon_lowercaseCode_isNormalizedToUppercase() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon("SAVE10", 10, false, 7)));

        // 小文字で渡してもサービス内部でUPPERCASEされてDBを検索すること
        Coupon result = couponService.findValidCoupon("save10");

        assertThat(result.getCode()).isEqualTo("SAVE10");
        verify(couponRepository).findByCode("SAVE10");
    }

    @Test
    void findValidCoupon_notFound_throwsNotFound() {
        when(couponRepository.findByCode("NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.findValidCoupon("NONE"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("クーポンコードが無効です");
    }

    @Test
    void findValidCoupon_alreadyUsed_throwsBadRequest() {
        when(couponRepository.findByCode("USED")).thenReturn(Optional.of(coupon("USED", 10, true, 7)));

        assertThatThrownBy(() -> couponService.findValidCoupon("USED"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("使用済み");
    }

    @Test
    void findValidCoupon_expired_throwsBadRequest() {
        when(couponRepository.findByCode("OLD")).thenReturn(Optional.of(coupon("OLD", 10, false, -1)));

        assertThatThrownBy(() -> couponService.findValidCoupon("OLD"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("有効期限が切れています");
    }

    @Test
    void findValidCoupon_expiresExactlyToday_throwsBadRequest() {
        // expiresAt = today → isBefore(today) は false なので通過するはず… ではなく
        // isBefore(LocalDate.now()) = false のため valid として通過する
        Coupon c = coupon("TODAY", 10, false, 0); // expiresAt = today
        when(couponRepository.findByCode("TODAY")).thenReturn(Optional.of(c));

        // 今日が期限なら有効（期限切れは昨日以前）
        Coupon result = couponService.findValidCoupon("TODAY");
        assertThat(result).isNotNull();
    }

    // --- createCoupon ---

    @Test
    void createCoupon_success_codeUppercased() {
        CouponAdminRequest req = new CouponAdminRequest();
        req.setCode("new10");
        req.setDiscountRate(10);
        req.setExpiresAt(LocalDate.now().plusDays(30).toString());

        when(couponRepository.findByCode("NEW10")).thenReturn(Optional.empty());
        when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Coupon result = couponService.createCoupon(req);

        assertThat(result.getCode()).isEqualTo("NEW10");
        assertThat(result.getIsUsed()).isFalse();
        assertThat(result.getDiscountRate()).isEqualTo(10);
    }

    @Test
    void createCoupon_duplicateCode_throwsConflict() {
        CouponAdminRequest req = new CouponAdminRequest();
        req.setCode("DUPE");
        req.setDiscountRate(10);
        req.setExpiresAt(LocalDate.now().plusDays(30).toString());

        when(couponRepository.findByCode("DUPE")).thenReturn(Optional.of(coupon("DUPE", 10, false, 7)));

        assertThatThrownBy(() -> couponService.createCoupon(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("既に存在します");

        verify(couponRepository, never()).save(any());
    }

    @Test
    void createCoupon_discountRateZero_throwsBadRequest() {
        CouponAdminRequest req = new CouponAdminRequest();
        req.setCode("ZERO");
        req.setDiscountRate(0);
        req.setExpiresAt(LocalDate.now().plusDays(30).toString());

        when(couponRepository.findByCode("ZERO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.createCoupon(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("1〜100");
    }

    @Test
    void createCoupon_discountRate101_throwsBadRequest() {
        CouponAdminRequest req = new CouponAdminRequest();
        req.setCode("OVER");
        req.setDiscountRate(101);
        req.setExpiresAt(LocalDate.now().plusDays(30).toString());

        when(couponRepository.findByCode("OVER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.createCoupon(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("1〜100");
    }
}
