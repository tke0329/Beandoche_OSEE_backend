package com.beandoche_osee_backend.domain.garment.service;

import com.beandoche_osee_backend.domain.category.entity.Category;
import com.beandoche_osee_backend.domain.category.repository.CategoryRepository;
import com.beandoche_osee_backend.domain.garment.dto.GarmentDetailResponse;
import com.beandoche_osee_backend.domain.garment.dto.GarmentPageResponse;
import com.beandoche_osee_backend.domain.garment.dto.GarmentSummaryResponse;
import com.beandoche_osee_backend.domain.garment.repository.GarmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class GarmentService {
    private final GarmentRepository repository;
    private final CategoryRepository categories;

    public GarmentService(GarmentRepository repository, CategoryRepository categories) {
        this.repository = repository;
        this.categories = categories;
    }

    public GarmentPageResponse getGarments(String categoryCode, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100 || !"LATEST".equals(sort)) {
            throw GarmentFailure.invalidRequest();
        }
        List<Category> activeCategories = categories.findActiveCategories();
        Map<Long, List<Long>> children = childrenByParent(activeCategories);
        Set<Long> visible = descendants(rootIds(activeCategories), children);
        Collection<Long> selected = visible;
        if (categoryCode != null) {
            Category category = activeCategories.stream()
                    .filter(candidate -> candidate.getCode().equals(categoryCode)
                            && visible.contains(candidate.getId()))
                    .findFirst().orElseThrow(GarmentFailure::categoryNotFound);
            selected = descendants(List.of(category.getId()), children);
        }
        if (selected.isEmpty()) {
            return new GarmentPageResponse(List.of(), page, size, 0, 0);
        }

        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        var result = repository.findPublished(selected, pageable);
        return new GarmentPageResponse(result.getContent().stream().map(GarmentSummaryResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public GarmentDetailResponse getGarment(Long id) {
        var garment = repository.findPublishedById(id).orElseThrow(GarmentFailure::garmentNotFound);
        List<Category> activeCategories = categories.findActiveCategories();
        Set<Long> visible = descendants(rootIds(activeCategories), childrenByParent(activeCategories));
        if (!visible.contains(garment.getCategory().getId())) {
            throw GarmentFailure.garmentNotFound();
        }
        return GarmentDetailResponse.from(garment);
    }

    private List<Long> rootIds(List<Category> activeCategories) {
        return activeCategories.stream().filter(category -> category.getParent() == null)
                .map(Category::getId).toList();
    }

    private Map<Long, List<Long>> childrenByParent(List<Category> activeCategories) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (Category category : activeCategories) {
            if (category.getParent() != null) {
                children.computeIfAbsent(category.getParent().getId(), key -> new ArrayList<>())
                        .add(category.getId());
            }
        }
        return children;
    }

    // 활성 루트에서 도달할 수 없는 노드는 비활성 조상 아래에 있으므로 제외한다.
    private Set<Long> descendants(Collection<Long> roots, Map<Long, List<Long>> children) {
        Set<Long> visited = new HashSet<>();
        var pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            Long id = pending.removeFirst();
            if (visited.add(id)) {
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return visited;
    }
}
