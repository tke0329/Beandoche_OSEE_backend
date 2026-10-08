package com.beandoche_osee_backend.domain.auth.controller;

import com.beandoche_osee_backend.domain.auth.service.GoogleLoginResult;
import com.beandoche_osee_backend.domain.auth.service.GoogleLoginService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/social/google")
public class GoogleLoginController {

    private final GoogleLoginService googleLoginService;

    public GoogleLoginController(GoogleLoginService googleLoginService) {
        this.googleLoginService = googleLoginService;
    }

    @PostMapping
    public ResponseEntity<GoogleLoginResponse> login(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleLoginResult result = googleLoginService.login(request.idToken());
        return ResponseEntity.ok(GoogleLoginResponse.from(result));
    }

    public record GoogleLoginRequest(
            @NotBlank(message = "Google 인증 정보가 필요합니다.") String idToken) {
    }
}
