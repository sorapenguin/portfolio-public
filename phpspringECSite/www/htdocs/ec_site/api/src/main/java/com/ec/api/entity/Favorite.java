package com.ec.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "favorite_table",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}),
       indexes = {
           @Index(name = "idx_favorite_user_id", columnList = "user_id")
       })
@Getter @Setter @NoArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_id")
    private Long favoriteId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "create_date")
    private LocalDate createDate;
}
