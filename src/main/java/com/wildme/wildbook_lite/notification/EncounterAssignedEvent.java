package com.wildme.wildbook_lite.notification;

import java.time.Instant;

/**
 * Published when an Encounter gets an assignee. Notification goes
 * directly to the assignee (single recipient), unlike the broadcast
 * EncounterCreatedEvent / EncounterPublishedEvent.
 */
public record EncounterAssignedEvent(
    Long encounterId,
    Long projectId,
    Long assigneeUserId,
    Long assignedByUserId,
    Instant assignedAt
) {}
