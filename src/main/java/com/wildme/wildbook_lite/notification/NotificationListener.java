package com.wildme.wildbook_lite.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wildme.wildbook_lite.project.ProjectMember;
import com.wildme.wildbook_lite.project.ProjectMemberRepository;

/**
 * Fan-out: when a new Encounter is committed, create one in-app
 * Notification row per project member (excluding the creator).
 *
 *  - @TransactionalEventListener(AFTER_COMMIT): only fans out if the
 *    Encounter commit succeeded.
 *  - @Async: off the request thread.
 *  - REQUIRES_NEW: the listener's own transaction is independent so a
 *    notification-write failure doesn't poison the originating tx.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final ProjectMemberRepository memberRepo;
    private final NotificationRepository notificationRepo;

    public NotificationListener(ProjectMemberRepository memberRepo,
                                NotificationRepository notificationRepo) {
        this.memberRepo = memberRepo;
        this.notificationRepo = notificationRepo;
    }

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEncounterCreated(EncounterCreatedEvent event) {
        List<ProjectMember> members = memberRepo.findByProjectId(event.projectId());
        log.info("[notification] fanning out encounter={} to {} project member(s)",
            event.encounterId(), members.size());

        for (ProjectMember m : members) {
            if (m.getUserId().equals(event.createdByUserId())) continue;
            notificationRepo.save(new Notification(
                m.getUserId(),
                Notification.Kind.ENCOUNTER_CREATED,
                "New encounter recorded",
                "User " + event.createdByUserId() + " added encounter #" + event.encounterId(),
                "encounter",
                event.encounterId()
            ));
        }
    }
}
