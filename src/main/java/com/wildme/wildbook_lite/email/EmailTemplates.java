package com.wildme.wildbook_lite.email;

import com.wildme.wildbook_lite.notification.EncounterAssignedEvent;
import com.wildme.wildbook_lite.notification.EncounterCreatedEvent;
import com.wildme.wildbook_lite.notification.EncounterPublishedEvent;

/**
 * Builds {@link EmailMessage}s from notification events. Pure
 * static methods — no Spring, no IO, trivially unit-testable.
 *
 * Why not Thymeleaf / Freemarker / Velocity:
 *  - Three short templates with a handful of placeholders each — a
 *    full templating engine is overkill and adds a parse-and-render
 *    step on every send.
 *  - String.format is good enough and obvious enough that future
 *    readers can grep for "user %d" and find where it's used.
 *  - If/when templates grow to "rich content with conditional
 *    sections", swap this class for a TemplateEngine-backed
 *    component without touching the listener or sender.
 *
 * Subject lines are deliberately short + scannable — they show up in
 * the inbox preview pane and shape whether the user clicks through.
 */
public final class EmailTemplates {

    private EmailTemplates() {}

    public static EmailMessage encounterCreated(String to, EncounterCreatedEvent e) {
        String subject = "[wildbook] New encounter recorded in your project";
        String body = """
            A new encounter was logged in a project you belong to.

              Encounter ID: %d
              Project ID:   %d
              Recorded by:  user %d
              Recorded at:  %s

            View it at: /encounters/%d

            You are receiving this because you are a member of the project.
            To stop these emails, set your `emailOptIn` flag to false.
            """.formatted(
                e.encounterId(),
                e.projectId(),
                e.createdByUserId(),
                e.createdAt(),
                e.encounterId());
        return new EmailMessage(to, subject, body);
    }

    public static EmailMessage encounterAssigned(String to, EncounterAssignedEvent e) {
        String subject = "[wildbook] An encounter was assigned to you";
        String body = """
            An encounter was assigned to you for review.

              Encounter ID: %d
              Project ID:   %d
              Assigned by:  user %d
              Assigned at:  %s

            Open it at: /encounters/%d
            """.formatted(
                e.encounterId(),
                e.projectId(),
                e.assignedByUserId(),
                e.assignedAt(),
                e.encounterId());
        return new EmailMessage(to, subject, body);
    }

    public static EmailMessage encounterPublished(String to, EncounterPublishedEvent e) {
        String subject = "[wildbook] An encounter was published";
        String body = """
            An encounter you may care about was just published.

              Encounter ID: %d
              Project ID:   %d
              Species:      %s
              Published by: user %d
              Published at: %s

            View it at: /encounters/%d
            """.formatted(
                e.encounterId(),
                e.projectId(),
                e.species() == null ? "unknown" : e.species(),
                e.publishedByUserId(),
                e.publishedAt(),
                e.encounterId());
        return new EmailMessage(to, subject, body);
    }
}
