package com.ec.api.controller;

import com.ec.api.dto.CategoryRequest;
import com.ec.api.dto.CategoryResponse;
import com.ec.api.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> getCategories() {
        return categoryService.getAll();
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(req));
    }

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return categoryService.update(id, req);
    }

    @DeleteMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
