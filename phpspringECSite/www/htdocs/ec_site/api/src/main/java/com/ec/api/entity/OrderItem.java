package com.ec.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "order_item_table", indexes = {
    @Index(name = "idx_order_item_order_id", columnList = "order_id")
})
@Getter @Setter @NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "image_name")
    private String imageName;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "update_date")
    private LocalDate updateDate;
}
