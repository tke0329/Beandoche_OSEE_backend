package com.beandoche_osee_backend.global.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtectedTestController {

    @GetMapping("/api/test/protected")
    public String protectedEndpoint() {
        return "ok";
    }
}
