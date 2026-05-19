package com.ec.api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "image_table")
@Getter @Setter @NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @OneToOne
    @JoinColumn(name = "product_id")
    @JsonIgnore
    private Product product;

    @Column(name = "image_name")
    private String imageName;

    @Column(name = "create_date")
    private LocalDate createDate;

    @Column(name = "update_date")
    private LocalDate updateDate;
}
