package com.beandoche_osee_backend.domain.auth.service;

public interface GoogleLoginService {

    SocialLoginResult login(String idToken);
}
