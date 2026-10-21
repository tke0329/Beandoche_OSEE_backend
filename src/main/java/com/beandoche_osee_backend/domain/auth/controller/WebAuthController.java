package com.beandoche_osee_backend.domain.auth.controller;

import java.time.Instant;
import java.util.List;
import com.beandoche_osee_backend.domain.auth.service.GoogleLoginService;
import com.beandoche_osee_backend.domain.auth.service.SocialLoginResult;
import com.beandoche_osee_backend.domain.auth.service.WebSessionService;
import com.beandoche_osee_backend.global.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/web")
public class WebAuthController {
    private final GoogleLoginService google;
    private final WebSessionService sessions;
    private final WebSessionCookies cookies;
    private final List<String> origins;
    private final com.beandoche_osee_backend.domain.auth.service.GoogleUnlinkCompletionService unlinkCompletion;

    public WebAuthController(GoogleLoginService google, WebSessionService sessions, WebSessionCookies cookies,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> origins,
            com.beandoche_osee_backend.domain.auth.service.GoogleUnlinkCompletionService unlinkCompletion) {
        this.google = google; this.sessions = sessions; this.cookies = cookies; this.origins = origins;
        this.unlinkCompletion = unlinkCompletion;
    }

    @PostMapping("/google")
    public ResponseEntity<?> google(@Valid @RequestBody GoogleLoginController.GoogleLoginRequest body, HttpServletRequest request) {
        if (!trusted(request)) return denied();
        return authenticated(google.login(body.idToken()), request);
    }

    @PostMapping("/session")
    public ResponseEntity<?> restore(@CookieValue(name = WebSessionCookies.NAME, required = false) String token, HttpServletRequest request) {
        if (!trusted(request)) return denied();
        return sessions.rotate(token).<ResponseEntity<?>>map(result -> authenticated(result, request))
                .orElseGet(() -> ResponseEntity.noContent()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store").build());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = WebSessionCookies.NAME, required = false) String token, HttpServletRequest request) {
        if (!trusted(request)) return denied();
        sessions.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear(request)).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/withdrawal/google-complete")
    public ResponseEntity<?> confirmGoogleUnlink(HttpServletRequest request) {
        if (!trusted(request)) return denied();
        unlinkCompletion.confirm(request.getHeader(HttpHeaders.AUTHORIZATION));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    private ResponseEntity<?> authenticated(SocialLoginResult result, HttpServletRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.issue(result, request))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(new SessionResponse(result.userId(), result.newUser(), "Bearer", result.accessToken(), result.accessTokenExpiresAt(), result.sessionExpiresAt()));
    }

    private boolean trusted(HttpServletRequest request) { return origins.contains(request.getHeader("Origin")); }
    private ResponseEntity<?> denied() {
        return ResponseEntity.status(403).body(ApiErrorResponse.of("RESOURCE_ACCESS_DENIED", "허용되지 않은 요청입니다."));
    }

    public record SessionResponse(Long userId, boolean newUser, String tokenType, String accessToken, Instant accessTokenExpiresAt, Instant sessionExpiresAt) { }
}
