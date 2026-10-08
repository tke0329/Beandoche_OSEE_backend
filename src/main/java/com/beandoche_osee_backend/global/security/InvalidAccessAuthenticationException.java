package com.beandoche_osee_backend.global.security;

import org.springframework.security.core.AuthenticationException;

public class InvalidAccessAuthenticationException extends AuthenticationException {

    public InvalidAccessAuthenticationException() {
        super("Access authentication failed");
    }
}
