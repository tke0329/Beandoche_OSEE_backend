package com.beandoche_osee_backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beandoche_osee_backend.domain.auth.repository.UserRepository;
import com.beandoche_osee_backend.domain.auth.token.AccessTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProtectedTestController.class)
@Import({SecurityConfig.class, BearerAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class SecurityCorsTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AccessTokenProvider accessTokenProvider;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("t1 configured frontend origin can preflight login without authentication")
    void t1_configuredOriginCanPreflightLogin() throws Exception {
        var response = mockMvc.perform(options("/api/auth/social/google")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    @DisplayName("t2 untrusted origin cannot preflight login")
    void t2_untrustedOriginCannotPreflightLogin() throws Exception {
        var response = mockMvc.perform(options("/api/auth/social/google")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andReturn().getResponse();
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    @DisplayName("t3 unapproved request header is rejected")
    void t3_unapprovedHeaderIsRejected() throws Exception {
        mockMvc.perform(options("/api/auth/social/google")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-unapproved"))
                .andExpect(status().isForbidden());
    }
}
