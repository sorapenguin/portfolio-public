package com.ec.api.dto;

import com.ec.api.entity.Category;
import lombok.Data;

@Data
public class CategoryResponse {
    private Long categoryId;
    private String categoryName;

    public static CategoryResponse from(Category c) {
        CategoryResponse r = new CategoryResponse();
        r.setCategoryId(c.getCategoryId());
        r.setCategoryName(c.getCategoryName());
        return r;
    }
}
