package com.beandoche_osee_backend.domain.profile;

import com.beandoche_osee_backend.global.security.AuthenticatedUserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {
    private final UserProfileService profiles;
    public UserProfileController(UserProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    public ResponseEntity<UserProfileService.ProfileResponse> get(@AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(profiles.get(user.userId()));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileService.ProfileResponse> save(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @RequestParam String nickname, @RequestParam(required = false) MultipartFile image,
            @RequestParam(defaultValue = "false") boolean removeImage) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(profiles.save(user.userId(), nickname, image, removeImage));
    }
}
