package com.wildme.wildbook_lite.auth;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Counters exported at /actuator/metrics:
 *   - auth.login.success
 *   - auth.login.failure
 *   - auth.register.success
 *
 * Spring Boot autoconfigures a MeterRegistry (Micrometer) when actuator
 * is on the classpath. Inject it, build Counters once, and increment.
 *
 *  - Tags are key-value labels on every metric. Use them for "facets"
 *    you want to slice by — NOT for high-cardinality data (don't tag by
 *    user id; you'll blow up the time-series store).
 *  - Counters are monotonic. Use Timer/DistributionSummary for latencies.
 *  - In production you'd plug Micrometer into Prometheus / Datadog /
 *    CloudWatch — same metric definitions, just a different registry.
 */
@Component
public class AuthMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter registerSuccess;

    public AuthMetrics(MeterRegistry registry) {
        this.loginSuccess = Counter.builder("auth.login.success")
            .description("Successful logins")
            .tag("kind", "password")
            .register(registry);

        this.loginFailure = Counter.builder("auth.login.failure")
            .description("Failed login attempts")
            .tag("kind", "password")
            .register(registry);

        this.registerSuccess = Counter.builder("auth.register.success")
            .description("New users registered")
            .register(registry);
    }

    public void onLoginSuccess()    { loginSuccess.increment(); }
    public void onLoginFailure()    { loginFailure.increment(); }
    public void onRegisterSuccess() { registerSuccess.increment(); }
}
