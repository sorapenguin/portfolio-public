package com.ec.api.service;

import com.ec.api.dto.CategoryRequest;
import com.ec.api.dto.CategoryResponse;
import com.ec.api.entity.Category;
import com.ec.api.repository.CategoryRepository;
import com.ec.api.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        if (categoryRepository.existsByCategoryName(req.getCategoryName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "カテゴリ名が既に存在します");
        }
        Category c = new Category();
        c.setCategoryName(req.getCategoryName());
        c.setCreateDate(LocalDate.now());
        c.setUpdateDate(LocalDate.now());
        return CategoryResponse.from(categoryRepository.save(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "カテゴリが見つかりません"));
        if (!c.getCategoryName().equals(req.getCategoryName()) &&
                categoryRepository.existsByCategoryName(req.getCategoryName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "カテゴリ名が既に存在します");
        }
        c.setCategoryName(req.getCategoryName());
        c.setUpdateDate(LocalDate.now());
        return CategoryResponse.from(categoryRepository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "カテゴリが見つかりません"));
        if (productRepository.countByCategory(c) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "このカテゴリは商品で使用されているため削除できません");
        }
        categoryRepository.delete(c);
    }
}
