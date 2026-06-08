package com.wildme.wildbook_lite.config;

import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import com.wildme.wildbook_lite.auth.UserRepository;
import com.wildme.wildbook_lite.notification.NotificationRepository;
import com.wildme.wildbook_lite.project.ProjectRepository;
import com.wildme.wildbook_lite.repository.EncounterRepository;

/**
 * Exposes domain-level counts at /actuator/domain.
 *
 * Spring Boot Actuator is *extensible*: you don't only get the
 * built-ins (/health, /info, /metrics). Annotate a bean with @Endpoint
 * and a method with @ReadOperation / @WriteOperation, and Actuator
 * publishes it next to its own endpoints — same media-type negotiation,
 * same JMX/web auto-registration, same security hooks.
 *
 *  - @Endpoint(id = "domain") → URL becomes /actuator/domain.
 *  - @ReadOperation → mapped to HTTP GET.
 *  - Method param types map to query params. Return value is JSON-rendered.
 *
 * This is the right way to expose internal-only metrics ("what's in my
 * DB?") without inventing a parallel "/internal/..." controller and
 * remembering to lock it down separately. Actuator endpoints inherit
 * the exposure / security rules from `management.endpoints.web.exposure.include`.
 */
@Component
@Endpoint(id = "domain")
public class DomainActuatorEndpoint {

    private final UserRepository userRepo;
    private final ProjectRepository projectRepo;
    private final EncounterRepository encRepo;
    private final NotificationRepository notifRepo;

    public DomainActuatorEndpoint(UserRepository userRepo,
                                  ProjectRepository projectRepo,
                                  EncounterRepository encRepo,
                                  NotificationRepository notifRepo) {
        this.userRepo = userRepo;
        this.projectRepo = projectRepo;
        this.encRepo = encRepo;
        this.notifRepo = notifRepo;
    }

    @ReadOperation
    public Map<String, Object> counts() {
        return Map.of(
            "users", userRepo.count(),
            "projects", projectRepo.count(),
            "encounters", encRepo.count(),
            "notifications", notifRepo.count()
        );
    }
}
