package com.beandoche_osee_backend.domain.garment.service;

public class GarmentFailure extends RuntimeException {
    private final int status;
    private final String code;

    private GarmentFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static GarmentFailure garmentNotFound() {
        return new GarmentFailure(404, "GARMENT_NOT_FOUND", "상품을 찾을 수 없습니다.");
    }

    public static GarmentFailure categoryNotFound() {
        return new GarmentFailure(404, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다.");
    }

    public static GarmentFailure invalidRequest() {
        return new GarmentFailure(400, "INVALID_REQUEST",
                "페이지는 0 이상, 크기는 1~100, 정렬은 LATEST로 요청해 주세요.");
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
