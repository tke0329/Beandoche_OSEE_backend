package com.beandoche_osee_backend.domain.garment.catalog;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.beandoche_osee_backend.domain.garment.catalog.CatalogData.*;

@Component
public class MeasurementValidator {
    public Validation validate(List<MeasurementRule> rules, List<Measurement> values) {
        List<ValidationIssue> issues = new ArrayList<>();
        var byId = new HashMap<Long, Measurement>();
        for (Measurement value : values) {
            if (byId.put(value.definitionId(), value) != null) {
                issues.add(new ValidationIssue("DUPLICATE", value.code(), "실측 항목이 중복되었습니다."));
            }
        }
        if (rules.stream().noneMatch(MeasurementRule::active)) {
            issues.add(new ValidationIssue("NO_MEASUREMENT_RULES", null, "카테고리의 측정 항목이 설정되지 않았습니다."));
        }
        if (values.isEmpty()) {
            issues.add(new ValidationIssue("NO_MEASUREMENTS", null, "등록된 실측이 없습니다."));
        }
        for (MeasurementRule rule : rules) {
            Measurement value = byId.remove(rule.id());
            if (!rule.active()) {
                if (rule.required() || value != null) {
                    issues.add(new ValidationIssue("INACTIVE_DEFINITION", rule.code(), "사용 중지된 측정 항목입니다."));
                }
                continue;
            }
            if (value == null) {
                if (rule.required()) {
                    issues.add(new ValidationIssue("MISSING_REQUIRED", rule.code(), "필수 실측이 없습니다."));
                }
                continue;
            }
            if (rule.measurementBasis() == MeasurementBasis.WEIGHT || !"mm".equals(rule.defaultUnit())
                    || !"mm".equals(value.unit())) {
                issues.add(new ValidationIssue("UNSUPPORTED_UNIT", rule.code(), "1차에서는 길이 실측(mm)만 지원합니다."));
            }
            if (value.valueMm() == null || value.valueMm().signum() <= 0) {
                issues.add(new ValidationIssue("NON_POSITIVE", rule.code(), "실측은 0보다 커야 합니다."));
            }
            if (rule.minValueMm() == null || rule.maxValueMm() == null) {
                issues.add(new ValidationIssue("RANGE_NOT_CONFIGURED", rule.code(), "허용 범위가 설정되지 않았습니다."));
            } else if (value.valueMm() != null && (value.valueMm().compareTo(rule.minValueMm()) < 0
                    || value.valueMm().compareTo(rule.maxValueMm()) > 0)) {
                issues.add(new ValidationIssue("OUT_OF_RANGE", rule.code(), "실측이 허용 범위를 벗어났습니다."));
            }
        }
        byId.values().forEach(value -> issues.add(new ValidationIssue(
                "NOT_ALLOWED", value.code(), "카테고리에서 허용하지 않는 측정 항목입니다.")));
        boolean invalid = issues.stream().anyMatch(issue -> switch (issue.code()) {
            case "NO_MEASUREMENT_RULES", "NO_MEASUREMENTS", "RANGE_NOT_CONFIGURED", "UNSUPPORTED_UNIT", "INACTIVE_DEFINITION" -> false;
            default -> true;
        });
        String status = issues.isEmpty() ? "VALID" : invalid ? "INVALID" : "UNAVAILABLE";
        return new Validation(status, issues.isEmpty(), List.copyOf(issues));
    }
}
