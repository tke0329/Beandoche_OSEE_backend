package com.beandoche_osee_backend.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtectedTestController {

    @GetMapping("/api/test/protected")
    public String protectedEndpoint(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return principal.userId().toString();
    }

    @GetMapping("/api/auth/test/public")
    public String publicEndpoint() {
        return "public";
    }
}
