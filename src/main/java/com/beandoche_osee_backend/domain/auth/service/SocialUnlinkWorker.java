package com.beandoche_osee_backend.domain.auth.service;

import java.time.Clock;
import com.beandoche_osee_backend.domain.auth.provider.kakao.KakaoUnlinkClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialUnlinkWorker {
    private final SocialUnlinkTasks tasks;
    private final KakaoUnlinkClient kakao;
    private final Clock clock;
    public SocialUnlinkWorker(SocialUnlinkTasks tasks, KakaoUnlinkClient kakao, Clock clock) {
        this.tasks = tasks; this.kakao = kakao; this.clock = clock;
    }

    @Scheduled(initialDelayString = "${auth.unlink.initial-delay:15000}", fixedDelayString = "${auth.unlink.retry-delay:60000}")
    @Transactional
    public void processPending() {
        for (var task : tasks.lockDue(clock.instant())) {
            var outcome = kakao.unlink(task.providerUserId());
            if (outcome == KakaoUnlinkClient.Outcome.COMPLETED) tasks.complete(task.id(), clock.instant());
            else tasks.retry(task, outcome == KakaoUnlinkClient.Outcome.CONFIG_REQUIRED, clock.instant());
        }
    }

    @Configuration @EnableScheduling
    static class Scheduling { }
}
