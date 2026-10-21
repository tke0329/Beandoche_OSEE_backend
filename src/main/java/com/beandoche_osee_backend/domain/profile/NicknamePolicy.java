package com.beandoche_osee_backend.domain.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class NicknamePolicy {
    private final List<String> blockedTerms;

    public NicknamePolicy() throws IOException {
        String terms = new ClassPathResource("policy/nickname-blocked-terms.txt").getContentAsString(StandardCharsets.UTF_8);
        blockedTerms = terms.lines().map(String::strip).filter(term -> !term.isEmpty()).toList();
    }

    public String normalize(String input) {
        return input == null ? "" : Normalizer.normalize(input, Normalizer.Form.NFKC).strip();
    }

    public void validate(String nickname) {
        if (!nickname.matches("[가-힣A-Za-z0-9_]{2,20}")) {
            throw new ProfileFailure("INVALID_NICKNAME", "닉네임은 한글·영문·숫자·밑줄 2~20자로 입력해 주세요.", 400);
        }
        String comparison = nickname.toLowerCase(Locale.ROOT).replaceAll("[0-9_]", "");
        if (blockedTerms.stream().anyMatch(comparison::contains)) {
            throw new ProfileFailure("NICKNAME_NOT_ALLOWED", "욕설이나 성적인 표현은 닉네임으로 사용할 수 없습니다.", 400);
        }
    }
}
