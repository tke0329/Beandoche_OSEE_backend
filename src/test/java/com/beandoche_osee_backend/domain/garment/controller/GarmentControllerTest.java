package com.beandoche_osee_backend.domain.garment.controller;

import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import com.beandoche_osee_backend.domain.garment.dto.GarmentDetailResponse;
import com.beandoche_osee_backend.domain.garment.dto.GarmentPageResponse;
import com.beandoche_osee_backend.domain.garment.dto.GarmentSummaryResponse;
import com.beandoche_osee_backend.domain.garment.service.GarmentFailure;
import com.beandoche_osee_backend.domain.garment.service.GarmentService;
import com.beandoche_osee_backend.global.security.BearerAuthenticationFilter;
import com.beandoche_osee_backend.global.security.RestAuthenticationEntryPoint;
import com.beandoche_osee_backend.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GarmentController.class)
@Import({SecurityConfig.class, BearerAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class GarmentControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private GarmentService service;
    @MockitoBean private AccessTokenProvider accessTokenProvider;
    @MockitoBean private UserRepository userRepository;

    @Test
    @WithMockUser
    @DisplayName("t1 인증된 목록 요청에 기본 페이지와 상품 요약을 반환한다")
    void t1_returnsPageWithDefaults() throws Exception {
        var summary = new GarmentSummaryResponse(7L, 11L, "SHIRT", "셔츠", "면 셔츠", "/shirt.jpg");
        given(service.getGarments(null, 0, 20, "LATEST"))
                .willReturn(new GarmentPageResponse(List.of(summary), 0, 20, 1, 1));
        mvc.perform(get("/api/garments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].categoryCode").value("SHIRT"))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("/shirt.jpg"))
                .andExpect(jsonPath("$.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("t2 카테고리와 페이지 및 정렬 입력을 Service에 전달한다")
    void t2_forwardsFilters() throws Exception {
        given(service.getGarments("TOP", 2, 5, "LATEST"))
                .willReturn(new GarmentPageResponse(List.of(), 2, 5, 0, 0));
        mvc.perform(get("/api/garments").param("categoryCode", "TOP").param("page", "2")
                        .param("size", "5").param("sort", "LATEST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(service).getGarments("TOP", 2, 5, "LATEST");
    }

    @Test
    @WithMockUser
    @DisplayName("t3 공개 상품의 상세 JSON을 반환한다")
    void t3_returnsDetail() throws Exception {
        given(service.getGarment(7L)).willReturn(new GarmentDetailResponse(
                7L, 11L, "SHIRT", "셔츠", "면 셔츠", "상품 설명", "/shirt.jpg", null, null));
        mvc.perform(get("/api/garments/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("상품 설명"))
                .andExpect(jsonPath("$.categoryId").value(11))
                .andExpect(jsonPath("$.brand").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("t4 조회 불가 상품은 일관된 404 오류를 반환한다")
    void t4_returnsSafeNotFound() throws Exception {
        given(service.getGarment(7L)).willThrow(GarmentFailure.garmentNotFound());
        mvc.perform(get("/api/garments/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GARMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("t5 숫자가 아닌 페이지와 ID 입력은 400이다")
    void t5_rejectsMalformedNumbers() throws Exception {
        mvc.perform(get("/api/garments").param("page", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/garments/abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("t6 인증 없이 목록과 상세에 접근할 수 없다")
    void t6_requiresAuthentication() throws Exception {
        for (String path : List.of("/api/garments", "/api/garments/7")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    @DisplayName("t7 유효하지 않은 목록 조건은 안전한 400 오류다")
    void t7_returnsInvalidRequest() throws Exception {
        given(service.getGarments(null, -1, 20, "LATEST")).willThrow(GarmentFailure.invalidRequest());
        mvc.perform(get("/api/garments").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
