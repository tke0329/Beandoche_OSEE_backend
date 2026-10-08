package com.beandoche_osee_backend.domain.auth.service;

import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdTokenVerifier;
import com.beandoche_osee_backend.domain.auth.provider.google.GoogleIdentity;
import org.springframework.stereotype.Service;

@Service
public class GoogleLoginApplicationService implements GoogleLoginService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final SocialLoginTransactionService socialLoginTransactionService;

    public GoogleLoginApplicationService(
            GoogleIdTokenVerifier googleIdTokenVerifier,
            SocialLoginTransactionService socialLoginTransactionService) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.socialLoginTransactionService = socialLoginTransactionService;
    }

    @Override
    public SocialLoginResult login(String idToken) {
        GoogleIdentity identity = googleIdTokenVerifier.verify(idToken);
        return socialLoginTransactionService.login(SocialProvider.GOOGLE, identity.providerUserId());
    }
}
