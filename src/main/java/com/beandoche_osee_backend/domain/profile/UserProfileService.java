package com.beandoche_osee_backend.domain.profile;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfileService {
    private final UserProfileRepository profiles;
    private final ProfileImageValidator images;
    private final Clock clock;
    public UserProfileService(UserProfileRepository profiles, ProfileImageValidator images, Clock clock) {
        this.profiles = profiles; this.images = images; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return profiles.findById(userId).map(this::response).orElse(new ProfileResponse(userId, null, null, false, null));
    }

    @Transactional
    public ProfileResponse save(Long userId, String input, MultipartFile image, boolean removeImage) {
        String nickname = input == null ? "" : Normalizer.normalize(input, Normalizer.Form.NFKC).strip();
        if (!nickname.matches("[가-힣A-Za-z0-9_]{2,20}")) throw new ProfileFailure("INVALID_NICKNAME", "닉네임은 한글·영문·숫자·밑줄 2~20자로 입력해 주세요.", 400);
        if (removeImage && image != null && !image.isEmpty()) throw new ProfileFailure("INVALID_PROFILE_IMAGE", "사진 변경과 삭제는 동시에 요청할 수 없습니다.", 400);
        byte[] data = images.validate(image);
        var profile = profiles.findLocked(userId).orElseGet(() -> new UserProfile(userId));
        if (!nickname.equals(profile.getNickname())) {
            if (profile.getNicknameUpdatedAt() != null && clock.instant().isBefore(profile.getNicknameUpdatedAt().plus(Duration.ofDays(30)))) {
                throw new ProfileFailure("NICKNAME_CHANGE_NOT_ALLOWED", "닉네임은 30일마다 변경할 수 있습니다. 다음 변경 가능 일시를 확인해 주세요.", 409);
            }
            String key = nickname.toLowerCase(Locale.ROOT);
            if (profiles.existsByNicknameKeyAndUserIdNot(key, userId)) throw new ProfileFailure("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.", 409);
            profile.rename(nickname, key, clock.instant());
        }
        if (data != null) profile.setImageData(data);
        if (removeImage) profile.setImageData(null);
        return response(profiles.saveAndFlush(profile));
    }

    private ProfileResponse response(UserProfile profile) {
        return new ProfileResponse(profile.getUserId(), profile.getNickname(),
                profile.getImageData() == null ? null : Base64.getEncoder().encodeToString(profile.getImageData()), true,
                profile.getNicknameUpdatedAt().plus(Duration.ofDays(30)));
    }

    public record ProfileResponse(Long userId, String nickname, String imageBase64, boolean profileCompleted, Instant nicknameNextChangeAt) { }
}
