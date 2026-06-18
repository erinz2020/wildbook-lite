package com.wildme.wildbook_lite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Single, typed, validated home for every "app.*" property.
 *
 * Why @ConfigurationProperties (instead of @Value sprinkled everywhere):
 *  - Type-safe: fail at startup if a value is missing / unparseable,
 *    not at the runtime point where it's first used.
 *  - Validated: Jakarta Validation runs at bind time. The secret too short?
 *    The whole app refuses to start (loud failure beats silent misuse).
 *  - Discoverable: IDE auto-complete in application.yml when
 *    spring-boot-configuration-processor is also on the classpath.
 *  - Refactor-safe: rename a field once, every consumer follows.
 *
 * Activated by `@EnableConfigurationProperties(AppProperties.class)` in
 * a configuration class (see SchedulingConfig).
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
    @Valid Jwt jwt,
    @Valid Storage storage,
    @Valid Scheduling scheduling,
    @Valid OpenSearch opensearch,
    @Valid Mail mail
) {
    public record Jwt(
        @NotBlank @Size(min = 32, message = "must be >= 32 bytes for HS256") String secret,
        @Min(1) long expirationMinutes,
        @Min(1) long refreshLifetimeDays
    ) {}

    public record Storage(
        @NotBlank String mediaDir
    ) {}

    public record Scheduling(
        boolean enabled,
        @NotBlank String tokenCleanupCron,
        @NotBlank String notificationCleanupCron,
        @Min(1) int notificationRetentionDays
    ) {}

    /**
     * OpenSearch connection info. `enabled=false` skips the client bean
     * entirely; the app boots without OS being up.
     *
     * username/password are optional — empty strings mean "no auth"
     * (matches the security-plugin-disabled docker-compose setup).
     */
    public record OpenSearch(
        boolean enabled,
        @NotBlank String host,
        @Min(1) int port,
        @NotBlank String scheme,
        @NotBlank String indexName,
        String username,
        String password
    ) {}

    /**
     * Outbound email config.
     *
     *  - `enabled=false` (the default) skips the EmailSender + EmailListener
     *    beans entirely, via @ConditionalOnProperty. The app boots fine
     *    without any SMTP infra — silent feature-flag for local dev / CI.
     *  - `fromAddress` is what appears in the From: header. Pick a value
     *    that survives SPF/DKIM at your real SMTP relay before flipping
     *    enabled=true in production.
     *  - SMTP host/port/credentials are read by Spring's MailSenderAutoConfiguration
     *    from the standard `spring.mail.*` keys; we keep `app.mail.*`
     *    narrowly focused on what only WE care about. The two namespaces
     *    intentionally don't collide.
     */
    public record Mail(
        boolean enabled,
        @NotBlank String fromAddress,
        @NotBlank @Size(max = 64) String fromName,
        @Min(1) int maxAttempts,
        @Min(0) long initialBackoffMillis
    ) {}
}
