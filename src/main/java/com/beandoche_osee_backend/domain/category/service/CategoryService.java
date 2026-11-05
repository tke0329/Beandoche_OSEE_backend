package com.beandoche_osee_backend.domain.category.service;

import com.beandoche_osee_backend.domain.category.dto.CategoryResponse;
import com.beandoche_osee_backend.domain.category.entity.Category;
import com.beandoche_osee_backend.domain.category.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getCategories() {
        List<Category> categories = categoryRepository.findActiveCategories();

        // 조회 결과를 부모가 없는 항목과 부모별 자식 목록으로 구분한다.
        List<Category> roots = new ArrayList<>();
        Map<Long, List<Category>> childrenByParent = new HashMap<>();

        for (Category category : categories) {
            Category parent = category.getParent();

            if (parent == null) {
                roots.add(category);
            } else {
                childrenByParent
                        .computeIfAbsent(
                                parent.getId(),
                                key -> new ArrayList<>()
                        )
                        .add(category);
            }
        }

        return roots.stream()
                .map(category -> toResponse(category, childrenByParent))
                .toList();
    }

    private CategoryResponse toResponse(
            Category category,
            Map<Long, List<Category>> childrenByParent
    ) {
        List<CategoryResponse> children = childrenByParent
                .getOrDefault(category.getId(), List.of())
                .stream()
                .map(child -> toResponse(child, childrenByParent))
                .toList();

        return new CategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                children
        );
    }
}
