package com.wildme.wildbook_lite.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.wildme.wildbook_lite.config.AppProperties;

/**
 * Thin wrapper around {@link JavaMailSender} that:
 *
 *  - Retries on transient SMTP failures ({@link MailException}) with
 *    exponential backoff. The most common SMTP failures (connection
 *    refused, 421 Service unavailable, 451 Local error) are inherently
 *    transient; a fixed-attempt retry recovers without operator
 *    involvement most of the time.
 *
 *  - Bails out via @Recover when retries are exhausted. We DO NOT
 *    re-throw — a dead SMTP relay shouldn't kill the @Async caller's
 *    thread or surface in any user-visible request. The recover method
 *    logs at WARN and that's it; the in-app Notification was already
 *    written by NotificationListener as the primary delivery channel.
 *
 * Why @ConditionalOnProperty:
 *   - app.mail.enabled=false skips this bean entirely. The EmailListener
 *     (also conditional) won't be created either, so the @Async event
 *     handler simply doesn't exist. Local dev / CI runs with no SMTP
 *     infra are happy.
 *
 * Why @Retryable values are literals (not SpEL-bound to AppProperties):
 *   - Spring Retry reads annotation attributes at proxy-build time;
 *     SpEL bean lookups (`#{@appProperties.mail.maxAttempts}`) work
 *     but require the bean name to match the resolved name of the
 *     @ConfigurationProperties bean — fragile when refactoring.
 *   - LocalAssetStore uses the same literal pattern; we follow suit
 *     for consistency. AppProperties.Mail keeps the values as
 *     documentation + the programmatic-retry escape hatch when we
 *     ever need runtime tunability.
 *
 * @Retryable + @Async note:
 *   - Both are AOP advice. @Retryable is provided by spring-retry's
 *     RetryOperationsInterceptor, @Async by AsyncExecutionInterceptor.
 *     They compose cleanly because they're both proxy-based: the @Async
 *     proxy schedules, then the inner @Retryable proxy retries within
 *     the worker thread. Order doesn't matter at our scale.
 */
@Component
@ConditionalOnProperty(value = "app.mail.enabled", havingValue = "true")
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final AppProperties.Mail mailProps;

    public EmailSender(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.mailProps = props.mail();
    }

    /**
     * Send one transactional email. Retries 3 times total (1 + 2 retries)
     * with exponential backoff (1s, 2s) on any {@link MailException}.
     *
     * Retry scope: `retryFor = MailException.class` covers the whole
     * Spring mail-exception hierarchy — MailAuthenticationException,
     * MailSendException, MailParseException. Anything else propagates
     * immediately (NPEs, runtime bugs in the template builder).
     */
    @Retryable(
        retryFor = MailException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000)
    )
    public void send(EmailMessage message) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(formatFrom());
        msg.setTo(message.to());
        msg.setSubject(message.subject());
        msg.setText(message.body());
        mailSender.send(msg);
        log.info("[email] sent to={} subject='{}'", message.to(), message.subject());
    }

    /**
     * @Recover signature MUST match the @Retryable method: same return
     * type, exception as first param, then the original method args.
     * Swallow + log — the in-app notification is the primary channel,
     * email is best-effort.
     */
    @Recover
    public void onRetriesExhausted(MailException ex, EmailMessage message) {
        log.warn("[email] giving up after retries: to={} subject='{}' err={}",
            message.to(), message.subject(), ex.toString());
    }

    private String formatFrom() {
        // RFC 5322 display-name addressing: "Name <address>". JavaMail
        // does the right thing if `Name` contains commas, but we keep
        // it plain — config validates @NotBlank already.
        return mailProps.fromName() + " <" + mailProps.fromAddress() + ">";
    }
}
