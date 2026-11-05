package com.beandoche_osee_backend.domain.category.service;

import com.beandoche_osee_backend.domain.category.entity.Category;
import com.beandoche_osee_backend.domain.category.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock private CategoryRepository repository;
    @InjectMocks private CategoryService service;

    @Test
    @DisplayName("t1 카테고리를 부모와 자식 구조로 변환한다")
    void t1_buildsCategoryTree() {
        Category top = category(1L, "TOP", null);
        Category outer = category(2L, "OUTERWEAR", null);
        Category shirt = category(11L, "SHIRT", top);
        Category jacket = category(21L, "JACKET", outer);
        given(repository.findActiveCategories()).willReturn(List.of(shirt, top, jacket, outer));

        var result = service.getCategories();

        assertThat(result).extracting(r -> r.code()).containsExactly("TOP", "OUTERWEAR");
        assertThat(result.getFirst().id()).isEqualTo(1L);
        assertThat(result.getFirst().name()).isEqualTo("TOP 이름");
        assertThat(result.getFirst().children()).extracting(r -> r.code()).containsExactly("SHIRT");
        assertThat(result.get(1).children()).extracting(r -> r.code()).containsExactly("JACKET");
        assertThat(result.getFirst().children().getFirst().children()).isEmpty();
        verify(repository).findActiveCategories();
    }

    @Test
    @DisplayName("t2 저장소가 정렬한 대분류와 형제 카테고리 순서를 유지한다")
    void t2_preservesRepositoryOrder() {
        Category top = category(8L, "TOP", null);
        Category outer = category(1L, "OUTERWEAR", null);
        Category first = category(30L, "FIRST", top);
        Category second = category(20L, "SECOND", top);
        given(repository.findActiveCategories()).willReturn(List.of(top, first, outer, second));

        var result = service.getCategories();

        assertThat(result).extracting(r -> r.code()).containsExactly("TOP", "OUTERWEAR");
        assertThat(result.getFirst().children()).extracting(r -> r.code())
                .containsExactly("FIRST", "SECOND");
    }

    @Test
    @DisplayName("t3 조회 결과가 없으면 빈 목록을 반환한다")
    void t3_returnsEmptyList() {
        given(repository.findActiveCategories()).willReturn(List.of());
        assertThat(service.getCategories()).isEmpty();
    }

    @Test
    @DisplayName("t4 비활성 부모가 조회에서 제외되면 그 아래 자손도 노출하지 않는다")
    void t4_hidesDescendantsOfMissingParent() {
        Category inactive = category(1L, "INACTIVE", null);
        ReflectionTestUtils.setField(inactive, "active", false);
        Category child = category(11L, "CHILD", inactive);
        Category grandchild = category(111L, "GRANDCHILD", child);
        Category visible = category(2L, "VISIBLE", null);
        given(repository.findActiveCategories()).willReturn(List.of(child, grandchild, visible));

        assertThat(service.getCategories()).extracting(r -> r.code()).containsExactly("VISIBLE");
    }

    @Test
    @DisplayName("t5 여러 깊이의 카테고리와 자식 없는 항목을 변환한다")
    void t5_mapsNestedCategories() {
        Category root = category(1L, "ROOT", null);
        Category child = category(2L, "CHILD", root);
        Category leaf = category(3L, "LEAF", child);
        given(repository.findActiveCategories()).willReturn(List.of(leaf, child, root));

        var result = service.getCategories().getFirst();

        assertThat(result.children().getFirst().children().getFirst().code()).isEqualTo("LEAF");
        assertThat(result.children().getFirst().children().getFirst().children()).isEmpty();
    }

    // 엔티티의 운영 API를 테스트 편의 때문에 변경하지 않고 fixture를 준비한다.
    private Category category(Long id, String code, Category parent) {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "code", code);
        ReflectionTestUtils.setField(category, "name", code + " 이름");
        ReflectionTestUtils.setField(category, "parent", parent);
        return category;
    }
}
