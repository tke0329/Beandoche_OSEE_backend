package com.beandoche_osee_backend.domain.auth.provider.kakao;

public class KakaoLoginException extends RuntimeException {
    private final String code;

    public KakaoLoginException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
