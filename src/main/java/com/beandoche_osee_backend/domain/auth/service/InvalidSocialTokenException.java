package com.beandoche_osee_backend.domain.auth.service;

public class InvalidSocialTokenException extends RuntimeException {

    public InvalidSocialTokenException() {
        super(null, null, false, false);
    }
}
