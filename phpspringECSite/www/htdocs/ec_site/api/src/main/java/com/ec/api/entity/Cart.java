package com.ec.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "cart_table", indexes = {
    @Index(name = "idx_cart_user_id", columnList = "user_id"),
    @Index(name = "idx_cart_user_product", columnList = "user_id,product_id")
})
@Getter @Setter @NoArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_qty", nullable = false)
    private Integer productQty;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "update_date")
    private LocalDate updateDate;
}
