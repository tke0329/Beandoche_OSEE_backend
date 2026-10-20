package com.beandoche_osee_backend.domain.auth.controller;

import java.time.Clock;
import java.time.Duration;
import com.beandoche_osee_backend.domain.auth.service.SocialLoginResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class WebSessionCookies {
    public static final String NAME = "osee_refresh";
    private final Clock clock;
    private final boolean secure;

    public WebSessionCookies(Clock clock, @Value("${auth.web.secure-cookie:false}") boolean secure) {
        this.clock = clock;
        this.secure = secure;
    }

    public String issue(SocialLoginResult result, HttpServletRequest request) {
        return cookie(result.refreshToken(), Math.max(0, Duration.between(clock.instant(), result.sessionExpiresAt()).toSeconds()), request);
    }

    public String clear(HttpServletRequest request) { return cookie("", 0, request); }

    private String cookie(String value, long seconds, HttpServletRequest request) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure || request.isSecure())
                .sameSite("Lax").path("/api/auth").maxAge(seconds).build().toString();
    }
}
