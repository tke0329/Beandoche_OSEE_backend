package com.beandoche_osee_backend.domain.auth.token;

public class RefreshSessionExpiredException extends RuntimeException {

    public RefreshSessionExpiredException() {
        super("Refresh session has expired");
    }
}
