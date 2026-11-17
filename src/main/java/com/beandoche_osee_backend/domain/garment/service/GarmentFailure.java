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

    public static GarmentFailure invalidOption() {
        return new GarmentFailure(400, "INVALID_GARMENT_OPTION", "선택할 수 없는 색상·사이즈 조합입니다.");
    }

    public static GarmentFailure catalogDataInvalid() {
        return new GarmentFailure(409, "CATALOG_DATA_INVALID", "상품 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
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
