package com.wildme.wildbook_lite.email;

/**
 * Internal value type for one outbound email — what
 * {@link EmailTemplates} produces and {@link EmailSender} consumes.
 *
 * Plaintext only by design:
 *  - Transactional emails carry a sentence or two of context plus a
 *    link. HTML adds rendering surface for very little gain and a
 *    lot of cross-client headaches (Outlook conditional comments,
 *    dark-mode color flips, etc.).
 *  - Once we genuinely need HTML, switch this to a sealed interface
 *    with PlainText / Html implementations; sender picks the API.
 *
 * The `to` address is per-recipient — we don't BCC project members
 * together to avoid leaking the recipient list across the project.
 */
public record EmailMessage(
    String to,
    String subject,
    String body
) {}
