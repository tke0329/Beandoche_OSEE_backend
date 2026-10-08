package com.beandoche_osee_backend.domain.auth.provider.google;

import com.beandoche_osee_backend.domain.auth.service.InvalidSocialTokenException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class GoogleIdTokenVerifier {

    private final JwtDecoder jwtDecoder;

    public GoogleIdTokenVerifier(@Qualifier("googleJwtDecoder") JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public GoogleIdentity verify(String idToken) {
        try {
            Jwt jwt = jwtDecoder.decode(requireToken(idToken));
            return new GoogleIdentity(requireSubject(jwt.getSubject()));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidSocialTokenException();
        }
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Google ID token must not be blank");
        }
        return token;
    }

    private static String requireSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Google subject must not be blank");
        }
        return subject;
    }
}
