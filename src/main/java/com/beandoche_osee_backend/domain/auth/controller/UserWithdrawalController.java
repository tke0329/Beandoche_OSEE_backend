package com.beandoche_osee_backend.domain.auth.controller;

import java.util.List;
import com.beandoche_osee_backend.domain.auth.service.UserWithdrawalService;
import com.beandoche_osee_backend.global.error.ApiErrorResponse;
import com.beandoche_osee_backend.global.security.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserWithdrawalController {
    private final UserWithdrawalService service;
    private final WebSessionCookies cookies;
    private final List<String> origins;
    public UserWithdrawalController(UserWithdrawalService service, WebSessionCookies cookies,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> origins) {
        this.service = service; this.cookies = cookies; this.origins = origins;
    }

    @DeleteMapping("/api/users/me")
    public ResponseEntity<?> withdraw(@AuthenticationPrincipal AuthenticatedUserPrincipal user, HttpServletRequest request) {
        if (!origins.contains(request.getHeader("Origin"))) return ResponseEntity.status(403).body(ApiErrorResponse.of("RESOURCE_ACCESS_DENIED", "허용되지 않은 요청입니다."));
        var result = service.withdraw(user.userId());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.SET_COOKIE, cookies.clear(request)).body(result);
    }
}
