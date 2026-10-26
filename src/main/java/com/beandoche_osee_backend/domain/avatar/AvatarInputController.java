package com.beandoche_osee_backend.domain.avatar;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.beandoche_osee_backend.domain.avatar.AvatarInputDtos.*;
import com.beandoche_osee_backend.global.security.AuthenticatedUserPrincipal;

@RestController
@RequestMapping("/api/users/me")
public class AvatarInputController {
    private final AvatarInputService service;

    public AvatarInputController(AvatarInputService service) {
        this.service = service;
    }

    @PutMapping("/body-profile")
    public ResponseEntity<BodyView> saveBody(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @Valid @RequestBody BodyRequest request) {
        return noStore(service.saveBody(user.userId(), request));
    }

    @GetMapping("/body-profile")
    public ResponseEntity<BodyView> getBody(@AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        return noStore(service.getBody(user.userId()));
    }

    @DeleteMapping("/body-profile")
    public ResponseEntity<Void> deleteBody(@AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        service.deleteBody(user.userId());
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GetMapping("/avatar")
    public ResponseEntity<AvatarView> getAvatar(@AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        return noStore(service.getAvatar(user.userId()));
    }

    @PatchMapping("/avatar/appearance")
    public ResponseEntity<AvatarView> saveAppearance(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @Valid @RequestBody AppearanceRequest request) {
        return noStore(service.saveAppearance(user.userId(), request));
    }

    @GetMapping("/avatar/generations/{id}/input.json")
    public ResponseEntity<ModelingInput> export(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"avatar-input-" + id + ".json\"")
                .body(service.exportInput(user.userId(), id));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
