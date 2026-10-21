package com.beandoche_osee_backend.domain.profile;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile")
public class UserProfile {
    @Id @Column(name = "user_id") private Long userId;
    @Column(nullable = false, length = 20) private String nickname;
    @Column(name = "nickname_key", nullable = false, length = 64) private String nicknameKey;
    @Column(name = "nickname_updated_at", nullable = false) private Instant nicknameUpdatedAt;
    @Column(name = "image_data", columnDefinition = "bytea") private byte[] imageData;

    protected UserProfile() { }
    public UserProfile(Long userId) { this.userId = userId; }
    public Long getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public Instant getNicknameUpdatedAt() { return nicknameUpdatedAt; }
    public byte[] getImageData() { return imageData; }
    public void rename(String nickname, String key, Instant at) {
        this.nickname = nickname; this.nicknameKey = key; this.nicknameUpdatedAt = at;
    }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
}
