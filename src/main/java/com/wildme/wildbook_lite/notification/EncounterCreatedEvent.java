package com.wildme.wildbook_lite.notification;

import java.time.Instant;

/**
 * Domain event. Stays a plain record — no Spring annotations.
 * Use ApplicationEventPublisher#publishEvent(event) to fire.
 */
public record EncounterCreatedEvent(
    Long encounterId,
    Long projectId,
    Long createdByUserId,
    Instant createdAt
) {}
