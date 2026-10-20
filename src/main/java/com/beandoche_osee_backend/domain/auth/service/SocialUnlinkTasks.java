package com.beandoche_osee_backend.domain.auth.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import com.beandoche_osee_backend.domain.auth.entity.SocialAccount;
import com.beandoche_osee_backend.domain.auth.entity.SocialProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SocialUnlinkTasks {
    private final JdbcTemplate jdbc;
    public SocialUnlinkTasks(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void enqueue(SocialAccount account, Instant now) {
        boolean manual = account.getProvider() == SocialProvider.GOOGLE;
        jdbc.update("insert into social_unlink_task(user_id,provider,provider_user_id,status,next_attempt_at,created_at,updated_at) values (?,?,?,?,?,?,?)",
                account.getUser().getId(), account.getProvider().name(), manual ? null : account.getProviderUserId(),
                manual ? "USER_ACTION_REQUIRED" : "PENDING", Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    public boolean hasPending(SocialProvider provider, String providerUserId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from social_unlink_task where provider=? and provider_user_id=? and status <> 'COMPLETED')",
                Boolean.class, provider.name(), providerUserId));
    }

    public List<Task> lockDue(Instant now) {
        return jdbc.query("select id,provider_user_id,attempts from social_unlink_task where provider='KAKAO' and status in ('PENDING','RETRY','CONFIG_REQUIRED') and next_attempt_at<=? order by id limit 5 for update skip locked",
                (rs, row) -> new Task(rs.getLong("id"), rs.getString("provider_user_id"), rs.getInt("attempts")), Timestamp.from(now));
    }

    public void complete(long id, Instant now) {
        jdbc.update("update social_unlink_task set status='COMPLETED',provider_user_id=null,last_error_code=null,updated_at=? where id=?", Timestamp.from(now), id);
    }

    public void retry(Task task, boolean configurationRequired, Instant now) {
        int attempts = task.attempts() + (configurationRequired ? 0 : 1);
        String status = configurationRequired ? "CONFIG_REQUIRED" : attempts >= 10 ? "USER_ACTION_REQUIRED" : "RETRY";
        long delay = configurationRequired ? 900 : Math.min(3600, 60L << Math.min(attempts, 6));
        jdbc.update("update social_unlink_task set status=?,attempts=?,last_error_code=?,next_attempt_at=?,updated_at=? where id=?",
                status, attempts, configurationRequired ? "KAKAO_ADMIN_CONFIGURATION_REQUIRED" : "KAKAO_UNLINK_FAILED",
                Timestamp.from(now.plusSeconds(delay)), Timestamp.from(now), task.id());
    }

    public record Task(long id, String providerUserId, int attempts) { }
}
