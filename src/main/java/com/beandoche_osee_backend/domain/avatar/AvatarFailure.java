package com.beandoche_osee_backend.domain.avatar;

public class AvatarFailure extends RuntimeException {
    private final String code;
    private final int status;

    public AvatarFailure(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}
