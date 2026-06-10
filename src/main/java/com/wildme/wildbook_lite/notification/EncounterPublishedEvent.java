package com.wildme.wildbook_lite.notification;

import java.time.Instant;

/**
 * Fired when an Encounter transitions into PUBLISHED. Listened to by
 * NotificationListener which fans out to all project members.
 */
public record EncounterPublishedEvent(
    Long encounterId,
    Long projectId,
    String species,
    Long publishedByUserId,
    Instant publishedAt
) {}
