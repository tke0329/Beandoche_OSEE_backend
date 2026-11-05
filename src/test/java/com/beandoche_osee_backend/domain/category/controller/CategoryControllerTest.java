package com.beandoche_osee_backend.domain.category.controller;

import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.category.dto.CategoryResponse;
import com.beandoche_osee_backend.domain.category.service.CategoryService;
import com.beandoche_osee_backend.global.security.BearerAuthenticationFilter;
import com.beandoche_osee_backend.global.security.RestAuthenticationEntryPoint;
import com.beandoche_osee_backend.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, BearerAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class CategoryControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CategoryService service;
    @MockitoBean private AccessTokenProvider accessTokenProvider;
    @MockitoBean private UserRepository userRepository;

    @Test
    @WithMockUser
    @DisplayName("t1 인증된 요청에 계층형 카테고리 JSON을 반환한다")
    void t1_returnsCategoryTree() throws Exception {
        var child = new CategoryResponse(11L, "SHIRT", "셔츠", List.of());
        given(service.getCategories()).willReturn(List.of(
                new CategoryResponse(1L, "TOP", "상의", List.of(child))));

        var response = mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("TOP"))
                .andExpect(jsonPath("$[0].name").value("상의"))
                .andExpect(jsonPath("$[0].children[0].id").value(11))
                .andExpect(jsonPath("$[0].children[0].code").value("SHIRT"))
                .andExpect(jsonPath("$[0].children[0].name").value("셔츠"))
                .andExpect(jsonPath("$[0].children[0].children").isEmpty())
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("parent", "active", "displayOrder");
    }

    @Test
    @WithMockUser
    @DisplayName("t2 카테고리가 없으면 빈 JSON 배열을 반환한다")
    void t2_returnsEmptyArray() throws Exception {
        given(service.getCategories()).willReturn(List.of());
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("t3 인증 없는 요청은 안전한 401 오류를 반환한다")
    void t3_rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray());
        verifyNoInteractions(service);
    }
}
