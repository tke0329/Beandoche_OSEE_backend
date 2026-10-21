package com.beandoche_osee_backend.domain.profile;

public class ProfileFailure extends RuntimeException {
    private final String code;
    private final int status;
    public ProfileFailure(String code, String message, int status) {
        super(message); this.code = code; this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
}
