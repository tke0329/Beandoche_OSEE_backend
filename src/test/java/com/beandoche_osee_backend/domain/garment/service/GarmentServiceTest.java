package com.beandoche_osee_backend.domain.garment.service;

import com.beandoche_osee_backend.domain.category.entity.Category;
import com.beandoche_osee_backend.domain.category.repository.CategoryRepository;
import com.beandoche_osee_backend.domain.garment.entity.Garment;
import com.beandoche_osee_backend.domain.garment.repository.GarmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GarmentServiceTest {
    @Mock private GarmentRepository repository;
    @Mock private CategoryRepository categories;
    @InjectMocks private GarmentService service;

    @Test
    @DisplayName("t1 대분류와 활성 자손의 상품을 최신순으로 페이지 조회한다")
    void t1_listsActiveDescendants() {
        Category top = category(1L, "TOP", null);
        Category shirt = category(11L, "SHIRT", top);
        Category leaf = category(111L, "LEAF", shirt);
        Category other = category(2L, "OTHER", null);
        Category missing = category(3L, "INACTIVE", null);
        given(categories.findActiveCategories()).willReturn(List.of(leaf, shirt, top, other,
                category(31L, "HIDDEN", missing)));
        Garment garment = garment(7L, leaf);
        given(repository.findPublished(anyCollection(), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<>(List.of(garment), invocation.getArgument(1), 3));

        var response = service.getGarments("TOP", 0, 2, "LATEST");

        assertThat(response.content()).extracting(r -> r.id()).containsExactly(7L);
        assertThat(response.content().getFirst().categoryCode()).isEqualTo("LEAF");
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        verify(repository).findPublished(argThat(ids -> ids.size() == 3
                && ids.containsAll(List.of(1L, 11L, 111L))), argThat(page -> page.getPageNumber() == 0
                && page.getPageSize() == 2 && page.getSort().getOrderFor("createdAt").isDescending()
                && page.getSort().getOrderFor("id").isDescending()));
    }

    @Test
    @DisplayName("t2 없는 카테고리와 비활성 조상 아래 카테고리는 404다")
    void t2_rejectsInvisibleCategory() {
        Category missing = category(1L, "INACTIVE", null);
        given(categories.findActiveCategories()).willReturn(List.of(category(11L, "HIDDEN", missing)));
        for (String code : List.of("MISSING", "HIDDEN")) {
            assertThatThrownBy(() -> service.getGarments(code, 0, 20, "LATEST"))
                    .isInstanceOfSatisfying(GarmentFailure.class, failure -> {
                        assertThat(failure.code()).isEqualTo("CATEGORY_NOT_FOUND");
                        assertThat(failure.status()).isEqualTo(404);
                    });
        }
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("t3 잘못된 페이지 크기와 미지원 정렬을 거부한다")
    void t3_validatesListParameters() {
        assertThatThrownBy(() -> service.getGarments(null, -1, 20, "LATEST")).isInstanceOf(GarmentFailure.class);
        assertThatThrownBy(() -> service.getGarments(null, 0, 0, "LATEST")).isInstanceOf(GarmentFailure.class);
        assertThatThrownBy(() -> service.getGarments(null, 0, 101, "LATEST")).isInstanceOf(GarmentFailure.class);
        assertThatThrownBy(() -> service.getGarments(null, 0, 20, "PRICE")).isInstanceOf(GarmentFailure.class);
        verifyNoInteractions(repository, categories);
    }

    @Test
    @DisplayName("t4 활성 카테고리가 없으면 조회 없이 빈 페이지를 반환한다")
    void t4_returnsEmptyPage() {
        given(categories.findActiveCategories()).willReturn(List.of());
        var response = service.getGarments(null, 0, 20, "LATEST");
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("t5 공개 상품의 공통 상세 정보를 반환한다")
    void t5_returnsDetail() {
        Category root = category(1L, "TOP", null);
        Category shirt = category(11L, "SHIRT", root);
        given(categories.findActiveCategories()).willReturn(List.of(root, shirt));
        given(repository.findPublishedById(7L)).willReturn(Optional.of(garment(7L, shirt)));
        var response = service.getGarment(7L);
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("테스트 상품");
        assertThat(response.description()).isEqualTo("상품 설명");
        assertThat(response.categoryId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("t6 비공개 또는 존재하지 않는 상품은 동일한 404다")
    void t6_hidesUnavailableGarment() {
        given(repository.findPublishedById(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGarment(7L))
                .isInstanceOfSatisfying(GarmentFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(404);
                    assertThat(failure.code()).isEqualTo("GARMENT_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("t7 비활성 조상 아래 상품의 상세를 숨긴다")
    void t7_hidesDetailUnderInactiveAncestor() {
        Category parent = category(1L, "INACTIVE", null);
        Category child = category(11L, "SHIRT", parent);
        given(categories.findActiveCategories()).willReturn(List.of(child));
        given(repository.findPublishedById(7L)).willReturn(Optional.of(garment(7L, child)));
        assertThatThrownBy(() -> service.getGarment(7L)).isInstanceOf(GarmentFailure.class);
    }

    private Category category(Long id, String code, Category parent) {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "code", code);
        ReflectionTestUtils.setField(category, "name", code);
        ReflectionTestUtils.setField(category, "parent", parent);
        return category;
    }

    private Garment garment(Long id, Category category) {
        Garment garment = BeanUtils.instantiateClass(Garment.class);
        ReflectionTestUtils.setField(garment, "id", id);
        ReflectionTestUtils.setField(garment, "category", category);
        ReflectionTestUtils.setField(garment, "name", "테스트 상품");
        ReflectionTestUtils.setField(garment, "description", "상품 설명");
        return garment;
    }
}
