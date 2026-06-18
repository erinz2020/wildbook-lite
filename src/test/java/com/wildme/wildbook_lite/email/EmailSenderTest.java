package com.wildme.wildbook_lite.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.wildme.wildbook_lite.config.AppProperties;

/**
 * Unit tests for {@link EmailSender}. Direct construction; no Spring.
 *
 * Important scope limit:
 *   @Retryable is a Spring AOP proxy. In these unit tests we hold the
 *   raw object (no proxy), so calls go straight to send() without
 *   any retry. We test the FUNCTIONAL contract — message shape sent
 *   to JavaMailSender, exception-propagation behaviour. The actual
 *   retry/recover wiring is covered by integration tests with a real
 *   Spring context (Testcontainers IT — gated on Docker).
 *
 *   This is the right trade-off: hand-rolling a retry test against the
 *   raw object would just be re-testing Spring Retry itself.
 */
@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    @Mock JavaMailSender mailSender;

    private EmailSender sut;

    @BeforeEach
    void setUp() {
        AppProperties props = appPropertiesWithMail(
            new AppProperties.Mail(true, "noreply@example.com", "wildbook-lite", 3, 1000));
        sut = new EmailSender(mailSender, props);
    }

    @Test
    @DisplayName("send passes a SimpleMailMessage with from/to/subject/body to JavaMailSender")
    void sendsCorrectShape() {
        sut.send(new EmailMessage("alice@example.com", "subject line", "the body"));

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(cap.capture());

        SimpleMailMessage sent = cap.getValue();
        assertThat(sent.getFrom()).isEqualTo("wildbook-lite <noreply@example.com>");
        assertThat(sent.getTo()).containsExactly("alice@example.com");
        assertThat(sent.getSubject()).isEqualTo("subject line");
        assertThat(sent.getText()).isEqualTo("the body");
    }

    @Test
    @DisplayName("MailException propagates to the caller (Spring Retry advice catches it in prod)")
    void mailExceptionPropagates() {
        doThrow(new MailSendException("SMTP refused")).when(mailSender).send(any(SimpleMailMessage.class));

        // Raw object — no Spring proxy → no retry → exception bubbles.
        // In a Spring-wired context the @Retryable would catch + retry,
        // and after maxAttempts the @Recover would swallow + log.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                sut.send(new EmailMessage("alice@example.com", "s", "b")))
            .isInstanceOf(MailSendException.class)
            .hasMessageContaining("SMTP refused");
    }

    @Test
    @DisplayName("@Recover signature: returns void, swallows, never re-sends")
    void recoverSwallows() {
        sut.onRetriesExhausted(new MailSendException("dead"),
            new EmailMessage("alice@example.com", "s", "b"));
        // Recover doesn't re-invoke send().
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ----- helpers -----

    private AppProperties appPropertiesWithMail(AppProperties.Mail mail) {
        return new AppProperties(
            new AppProperties.Jwt("test-secret-please-use-at-least-32-bytes-here-aaaaaaaa", 60, 30),
            new AppProperties.Storage("./tmp"),
            new AppProperties.Scheduling(false, "0 0 3 * * *", "0 0 4 * * *", 30),
            new AppProperties.OpenSearch(false, "localhost", 9200, "http", "encounters", "", ""),
            mail
        );
    }
}
