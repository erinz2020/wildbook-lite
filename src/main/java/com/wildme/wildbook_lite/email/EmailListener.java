package com.wildme.wildbook_lite.email;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wildme.wildbook_lite.auth.User;
import com.wildme.wildbook_lite.auth.UserRepository;
import com.wildme.wildbook_lite.notification.EncounterAssignedEvent;
import com.wildme.wildbook_lite.notification.EncounterCreatedEvent;
import com.wildme.wildbook_lite.notification.EncounterPublishedEvent;
import com.wildme.wildbook_lite.project.ProjectMember;
import com.wildme.wildbook_lite.project.ProjectMemberRepository;

/**
 * Email side-channel for the same encounter events that
 * {@link com.wildme.wildbook_lite.notification.NotificationListener}
 * handles for in-app notifications.
 *
 * Architecture: two parallel listeners on the same event stream, NOT
 * one listener doing both jobs. Why split:
 *   - Single-responsibility: in-app notification rows commit even if
 *     SMTP is down; email failures don't poison the notification path.
 *   - Independent feature flag: @ConditionalOnProperty gates this
 *     bean WITHOUT touching the notification listener. Run with mail
 *     off in dev, mail on in prod.
 *   - Independent retry strategy: notifications go in one tx;
 *     each email gets its own retry budget inside EmailSender.
 *
 * Why AFTER_COMMIT: don't ever email "encounter created" if the
 * originating transaction rolled back. AFTER_COMMIT is the only safe
 * phase for irreversible side effects.
 *
 * Why @Async: an SMTP RTT can be hundreds of ms; never hold the
 * request thread for that. Spring's applicationTaskExecutor handles
 * the dispatch.
 *
 * Why per-recipient send (not BCC):
 *   - BCC across project members would leak the recipient list to any
 *     mail client that exposes Bcc to its user (some do, despite the spec).
 *   - Per-recipient send is also easier to audit: one log line per
 *     email, with a clear to= address.
 *   - Throughput cost: O(N) SMTP calls. For project sizes we model
 *     (~50 members tops), trivially absorbable; if/when we move to
 *     a separate ESP (SES, SendGrid), they all accept bulk APIs that
 *     remove this concern.
 *
 * Recipient filter: skip users where `emailOptIn=false` OR `email`
 * is blank. We DO NOT skip disabled users — a disabled user can still
 * be a project member by historical accident; the safer default is to
 * deliver. This is a one-line change in {@link #wantsEmail(User)} if
 * the policy ever flips.
 */
@Component
@ConditionalOnProperty(value = "app.mail.enabled", havingValue = "true")
public class EmailListener {

    private static final Logger log = LoggerFactory.getLogger(EmailListener.class);

    private final ProjectMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final EmailSender emailSender;

    public EmailListener(ProjectMemberRepository memberRepo,
                         UserRepository userRepo,
                         EmailSender emailSender) {
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.emailSender = emailSender;
    }

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onEncounterCreated(EncounterCreatedEvent event) {
        Set<Long> recipients = projectRecipients(event.projectId(), event.createdByUserId());
        log.info("[email] encounter.created project={} → emailing {} recipient(s)",
            event.projectId(), recipients.size());

        for (User u : optedInUsers(recipients)) {
            emailSender.send(EmailTemplates.encounterCreated(u.getEmail(), event));
        }
    }

    /** Direct send to the assignee — one recipient, no fan-out. */
    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onEncounterAssigned(EncounterAssignedEvent event) {
        Optional<User> userOpt = userRepo.findById(event.assigneeUserId())
            .filter(this::wantsEmail);
        if (userOpt.isEmpty()) {
            log.info("[email] encounter.assigned assignee={} skipped (no email / opted out)",
                event.assigneeUserId());
            return;
        }
        emailSender.send(EmailTemplates.encounterAssigned(userOpt.get().getEmail(), event));
    }

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onEncounterPublished(EncounterPublishedEvent event) {
        Set<Long> recipients = projectRecipients(event.projectId(), event.publishedByUserId());
        log.info("[email] encounter.published project={} → emailing {} recipient(s)",
            event.projectId(), recipients.size());

        for (User u : optedInUsers(recipients)) {
            emailSender.send(EmailTemplates.encounterPublished(u.getEmail(), event));
        }
    }

    // ----- helpers -----

    /**
     * Members of the project, excluding the user who triggered the
     * event (you don't email yourself about your own action).
     * Returned as a Set so any future de-dup is automatic.
     */
    private Set<Long> projectRecipients(Long projectId, Long actorUserId) {
        List<ProjectMember> members = memberRepo.findByProjectId(projectId);
        Set<Long> ids = new HashSet<>(members.size());
        for (ProjectMember m : members) {
            if (!m.getUserId().equals(actorUserId)) ids.add(m.getUserId());
        }
        return ids;
    }

    /**
     * Resolve a set of user ids into User entities that have a
     * non-blank email and emailOptIn=true. Single batched lookup, then
     * filter — avoids the per-row findById N+1.
     */
    private List<User> optedInUsers(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return userRepo.findAllById(ids).stream()
            .filter(this::wantsEmail)
            .toList();
    }

    private boolean wantsEmail(User u) {
        return u.isEmailOptIn()
            && u.getEmail() != null
            && !u.getEmail().isBlank();
    }
}
