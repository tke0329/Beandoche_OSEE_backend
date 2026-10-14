package com.beandoche_osee_backend.domain.auth.service;

public class SocialProviderUnavailableException extends RuntimeException {

    public SocialProviderUnavailableException() {
        super(null, null, false, false);
    }
}
