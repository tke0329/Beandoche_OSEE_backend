package com.beandoche_osee_backend.domain.auth.token;

public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException() {
        super("Access token is invalid");
    }
}
