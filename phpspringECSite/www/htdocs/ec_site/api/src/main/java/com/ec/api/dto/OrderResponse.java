package com.ec.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private Integer totalPrice;
    private String status;
    private String createDate;
    private List<OrderItemResponse> items;
    private String userName;
    private Integer discountRate;
    private Integer discountAmount;

    @Data
    @AllArgsConstructor
    public static class OrderItemResponse {
        private String productName;
        private Integer price;
        private Integer qty;
        private String imageName;
    }
}
