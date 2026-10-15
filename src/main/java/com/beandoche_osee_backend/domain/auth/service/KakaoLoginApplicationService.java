package com.beandoche_osee_backend.domain.auth.service;

import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoApiClient;
import org.springframework.stereotype.Service;

@Service
public class KakaoLoginApplicationService {
    private final KakaoApiClient client;
    private final SocialLoginTransactionService transactions;

    public KakaoLoginApplicationService(KakaoApiClient client, SocialLoginTransactionService transactions) {
        this.client = client;
        this.transactions = transactions;
    }

    public SocialLoginResult login(String code) {
        return transactions.login(SocialProvider.KAKAO, client.verifyCode(code));
    }
}
