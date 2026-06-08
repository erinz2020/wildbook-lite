package com.wildme.wildbook_lite.auth;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily job: delete refresh tokens that are either expired or revoked.
 *
 * Spring Boot bits:
 *
 *  - @Scheduled(cron = ...)
 *      Read from `app.scheduling.token-cleanup-cron`. Spring evaluates the
 *      cron expression and a single TaskScheduler thread fires this on
 *      schedule. (Long-running schedulers should configure a dedicated
 *      executor so they don't block the next tick.)
 *
 *  - @ConditionalOnProperty
 *      Spring loads this bean only when `app.scheduling.enabled=true`.
 *      `matchIfMissing = true` → absent prop defaults to enabled.
 *      Lets you turn off ALL scheduled jobs from config without code.
 *
 *  - @Modifying + bulk delete on a JpaRepository method
 *      Skips the persistence context. Cheap and correct for cleanup
 *      that doesn't need entity lifecycle callbacks.
 */
@Component
@ConditionalOnProperty(value = "app.scheduling.enabled", matchIfMissing = true)
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository repo;

    public RefreshTokenCleanupJob(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    @Scheduled(cron = "${app.scheduling.token-cleanup-cron}")
    @Transactional
    public void run() {
        int deleted = repo.deleteExpiredOrRevoked(Instant.now());
        if (deleted > 0) {
            log.info("[cleanup] refresh tokens purged: {}", deleted);
        }
    }
}
