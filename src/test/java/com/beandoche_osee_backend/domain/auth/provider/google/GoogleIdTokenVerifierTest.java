package com.beandoche_osee_backend.domain.auth.provider.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Map;

import com.beandoche_osee_backend.domain.auth.service.InvalidSocialTokenException;
import com.beandoche_osee_backend.domain.auth.service.SocialProviderUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.client.ResourceAccessException;

class GoogleIdTokenVerifierTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(jwtDecoder);

    @Test
    @DisplayName("t1 verified Google id token returns the immutable subject")
    void t1_verifiedGoogleIdTokenReturnsImmutableSubject() {
        given(jwtDecoder.decode("valid-token")).willReturn(jwt("google-subject-1"));

        GoogleIdentity identity = verifier.verify("valid-token");

        assertThat(identity.providerUserId()).isEqualTo("google-subject-1");
    }

    @Test
    @DisplayName("t2 token rejected by the decoder becomes a safe social token failure")
    void t2_tokenRejectedByDecoderBecomesSafeSocialTokenFailure() {
        given(jwtDecoder.decode("invalid-token")).willThrow(mock(JwtValidationException.class));

        assertThatThrownBy(() -> verifier.verify("invalid-token"))
                .isExactlyInstanceOf(InvalidSocialTokenException.class)
                .hasMessage(null);
    }

    @Test
    @DisplayName("t3 token without a subject is rejected")
    void t3_tokenWithoutSubjectIsRejected() {
        given(jwtDecoder.decode("subjectless-token")).willReturn(jwt(" "));

        assertThatThrownBy(() -> verifier.verify("subjectless-token"))
                .isExactlyInstanceOf(InvalidSocialTokenException.class);
    }

    @Test
    @DisplayName("t4 Google key endpoint connection failure becomes a retryable provider failure")
    void t4_googleKeyEndpointConnectionFailureBecomesRetryableProviderFailure() {
        given(jwtDecoder.decode("provider-outage-token")).willThrow(new org.springframework.security.oauth2.jwt.JwtException(
                "Google key lookup failed",
                new ResourceAccessException("connection failed")));

        assertThatThrownBy(() -> verifier.verify("provider-outage-token"))
                .isExactlyInstanceOf(SocialProviderUnavailableException.class)
                .hasMessage(null);
    }

    private Jwt jwt(String subject) {
        Instant issuedAt = Instant.parse("2026-10-08T00:00:00Z");
        return new Jwt(
                "redacted-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", subject));
    }
}
