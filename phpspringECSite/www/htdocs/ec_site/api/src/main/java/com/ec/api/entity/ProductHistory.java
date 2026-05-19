package com.ec.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_history")
@Getter @Setter @NoArgsConstructor
public class ProductHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "operation", nullable = false)
    private String operation; // CREATE / UPDATE / DELETE

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "price")
    private Integer price;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "stock_qty")
    private Integer stockQty;

    @Column(name = "public_flg")
    private Integer publicFlg;

    @Column(name = "deleted_flg")
    private Integer deletedFlg;
}
