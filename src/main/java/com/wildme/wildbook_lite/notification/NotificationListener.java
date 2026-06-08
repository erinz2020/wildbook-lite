package com.wildme.wildbook_lite.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for domain events and acts asynchronously.
 *
 * Key choices (interview talking points):
 *
 *  - @TransactionalEventListener(phase = AFTER_COMMIT) — fires only after
 *    the publisher's transaction commits successfully. Prevents the classic
 *    bug "we sent the welcome email but the user row was rolled back".
 *
 *  - @Async — runs on AsyncConfig#applicationTaskExecutor, NOT the request
 *    thread. The HTTP response returns immediately; notification work
 *    happens in background.
 *
 *  - Stand-in for real email/SMS: just logs. Replace with mail client later.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEncounterCreated(EncounterCreatedEvent event) {
        log.info("[notification] new Encounter id={} in project={} by user={} at={}",
            event.encounterId(),
            event.projectId(),
            event.createdByUserId(),
            event.createdAt());
        // TODO: hook up real email/push when needed
    }
}
