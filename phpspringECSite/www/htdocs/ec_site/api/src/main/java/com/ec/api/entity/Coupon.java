package com.ec.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "coupon_table")
@Getter @Setter @NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "discount_rate", nullable = false)
    private Integer discountRate;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Column(name = "is_used", nullable = false)
    private Boolean isUsed = false;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "update_date")
    private LocalDate updateDate;
}
