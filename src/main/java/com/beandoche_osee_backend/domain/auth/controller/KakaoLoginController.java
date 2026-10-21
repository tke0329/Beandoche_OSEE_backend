package com.beandoche_osee_backend.domain.auth.controller;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoLoginException;
import com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoLoginStateStore;
import com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoOAuthProperties;
import com.beandoche_osee_backend.domain.auth.service.KakaoLoginApplicationService;
import com.beandoche_osee_backend.domain.auth.service.SocialLoginNotAllowedException;
import com.beandoche_osee_backend.domain.auth.service.SocialLoginResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/auth/social/kakao")
public class KakaoLoginController {
    private static final Logger LOG = LoggerFactory.getLogger(KakaoLoginController.class);
    private static final String COOKIE = "osee_kakao_browser";
    private final KakaoOAuthProperties properties;
    private final KakaoLoginStateStore states;
    private final KakaoLoginApplicationService service;
    private final ObjectMapper mapper;
    private final WebSessionCookies sessionCookies;
    private final SecureRandom random = new SecureRandom();

    public KakaoLoginController(KakaoOAuthProperties properties, KakaoLoginStateStore states,
            KakaoLoginApplicationService service, ObjectMapper mapper, WebSessionCookies sessionCookies) {
        this.properties = properties;
        this.states = states;
        this.service = service;
        this.mapper = mapper;
        this.sessionCookies = sessionCookies;
    }

    @GetMapping("/authorize")
    public ResponseEntity<String> authorize(HttpServletRequest request) {
        try {
            properties.requireConfigured();
            var issued = states.issue();
            var location = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                    .queryParam("response_type", "code").queryParam("client_id", properties.restApiKey())
                    .queryParam("redirect_uri", properties.redirectUri()).queryParam("state", issued.state())
                    .build().encode().toUri();
            return ResponseEntity.status(302).location(location).header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.SET_COOKIE, cookie(issued.binding(), 300, request)).body("");
        } catch (KakaoLoginException exception) {
            return popup(Map.of("ok", false, "code", exception.code()), request);
        }
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @CookieValue(name = COOKIE, required = false) String binding,
            HttpServletRequest request) {
        try {
            states.consume(state, binding);
            if (error != null) throw new KakaoLoginException("access_denied".equals(error) ? "KAKAO_CANCELED" : "KAKAO_LOGIN_FAILED");
            SocialLoginResult result = service.login(code);
            return popup(Map.of("ok", true, "session", Map.of(
                    "userId", result.userId(), "newUser", result.newUser(), "tokenType", "Bearer",
                    "accessToken", result.accessToken(), "accessTokenExpiresAt", result.accessTokenExpiresAt().toString(),
                    "sessionExpiresAt", result.sessionExpiresAt().toString())), request, result);
        } catch (KakaoLoginException exception) {
            LOG.info("Kakao login rejected: {}", exception.code());
            return popup(Map.of("ok", false, "code", exception.code()), request);
        } catch (SocialLoginNotAllowedException exception) {
            return popup(Map.of("ok", false, "code", "ACCOUNT_NOT_ALLOWED"), request);
        } catch (RuntimeException exception) {
            LOG.warn("Kakao callback could not complete ({})", exception.getClass().getSimpleName());
            return popup(Map.of("ok", false, "code", "KAKAO_UNAVAILABLE"), request);
        }
    }

    private ResponseEntity<String> popup(Map<String, Object> result, HttpServletRequest request) {
        return popup(result, request, null);
    }

    private ResponseEntity<String> popup(Map<String, Object> result, HttpServletRequest request, SocialLoginResult session) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        String payload = scriptJson(Map.of("type", "osee:kakao-login", "result", result));
        String origin = scriptJson(properties.frontendOrigin() == null ? "http://localhost:5173" : properties.frontendOrigin());
        String html = "<!doctype html><html lang=\"ko\"><meta charset=\"utf-8\"><title>OSEE 로그인</title>"
                + "<p>로그인 화면으로 돌아가 주세요. 이 창은 닫아도 됩니다.</p><script nonce=\"" + nonce
                + "\">if(window.opener){window.opener.postMessage(" + payload + "," + origin
                + ");window.close();}</script></html>";
        var response = ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookie("", 0, request))
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'none'; script-src 'nonce-" + nonce + "'; frame-ancestors 'none'; base-uri 'none'");
        if (session != null) response.header(HttpHeaders.SET_COOKIE, sessionCookies.issue(session, request));
        return response.body(html);
    }

    private String cookie(String value, int maxAge, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(request.isSecure()).sameSite("Lax")
                .path("/api/auth/social/kakao").maxAge(maxAge).build().toString();
    }

    private String scriptJson(Object value) {
        return mapper.writeValueAsString(value).replace("<", "\\u003c").replace(">", "\\u003e")
                .replace("&", "\\u0026").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
}
