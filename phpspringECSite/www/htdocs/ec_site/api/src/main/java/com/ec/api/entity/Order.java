package com.ec.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "order_table", indexes = {
    @Index(name = "idx_order_user_id", columnList = "user_id")
})
@Getter @Setter @NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "update_date")
    private LocalDate updateDate;
}
