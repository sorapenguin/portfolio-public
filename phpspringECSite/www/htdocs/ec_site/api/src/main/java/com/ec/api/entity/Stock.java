package com.ec.api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "stock_table")
@Getter @Setter @NoArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long stockId;

    @OneToOne
    @JoinColumn(name = "product_id")
    @JsonIgnore
    private Product product;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "update_date")
    private LocalDate updateDate;
}
