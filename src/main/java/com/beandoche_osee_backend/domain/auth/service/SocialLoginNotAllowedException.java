package com.beandoche_osee_backend.domain.auth.service;

public class SocialLoginNotAllowedException extends RuntimeException {

    public SocialLoginNotAllowedException() {
        super(null, null, false, false);
    }
}
