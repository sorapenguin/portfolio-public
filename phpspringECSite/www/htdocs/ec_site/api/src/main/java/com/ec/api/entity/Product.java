package com.ec.api.entity;

import com.ec.api.entity.Category;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "product_table", indexes = {
    @Index(name = "idx_product_category_id",   columnList = "category_id"),
    @Index(name = "idx_product_public_deleted", columnList = "public_flg,deleted_flg")
})
@Getter @Setter @NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "public_flg", nullable = false)
    private Integer publicFlg;

    @Column(name = "deleted_flg", columnDefinition = "INTEGER DEFAULT 0")
    private Integer deletedFlg = 0;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "update_date")
    private LocalDate updateDate;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.EAGER, optional = true)
    private Stock stock;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.EAGER, optional = true)
    private ProductImage image;
}
