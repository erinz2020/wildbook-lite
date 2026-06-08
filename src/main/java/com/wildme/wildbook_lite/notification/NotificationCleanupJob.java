package com.wildme.wildbook_lite.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.config.AppProperties;

/**
 * Daily job: drop read notifications older than the configured retention.
 * Same Spring Boot pattern as RefreshTokenCleanupJob; calling out the
 * pieces that differ:
 *
 *  - Uses AppProperties for the retention window — typed config beats
 *    `@Value("${...}")` once a service touches multiple props.
 *  - Cron expression is *also* injected from config so ops can tune
 *    timing without redeploying.
 */
@Component
@ConditionalOnProperty(value = "app.scheduling.enabled", matchIfMissing = true)
public class NotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationRepository repo;
    private final AppProperties props;

    public NotificationCleanupJob(NotificationRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Scheduled(cron = "${app.scheduling.notification-cleanup-cron}")
    @Transactional
    public void run() {
        Instant threshold = Instant.now()
            .minus(props.scheduling().notificationRetentionDays(), ChronoUnit.DAYS);
        int deleted = repo.deleteReadBefore(threshold);
        if (deleted > 0) {
            log.info("[cleanup] read notifications purged: {} (older than {})", deleted, threshold);
        }
    }
}
