package com.wildme.wildbook_lite.common;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.wildme.wildbook_lite.audit.AuditedEvent;
import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.config.TraceIdFilter;

/**
 * Cross-cutting audit for @Audited methods. Two side effects:
 *  1) Synchronous SLF4J log line (useful during development).
 *  2) Async ApplicationEvent → AuditLogListener persists to DB.
 *
 * Interview points:
 *
 *  - Spring AOP is *proxy-based*: same-class internal calls do NOT trigger
 *    the aspect. Same gotcha applies to @Transactional, @Cacheable, @Async.
 *  - JDK dynamic proxy (interfaces) vs CGLIB (concrete classes); Spring
 *    auto-picks based on bean type. Force CGLIB with @EnableAspectJAutoProxy(proxyTargetClass = true).
 *  - We always rethrow — never swallow. Audits should never change behavior.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger("audit");

    private final ApplicationEventPublisher events;

    public AuditAspect(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Around("@annotation(audited) || @within(audited)")
    public Object aroundAudited(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        String action = audited.value();
        if (action == null || action.isBlank()) {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            action = sig.getDeclaringType().getSimpleName() + "." + sig.getName();
        }

        Long userId = SecurityUtils.currentPrincipal().map(p -> p.getUserId()).orElse(null);
        String username = SecurityUtils.currentPrincipal().map(p -> p.getUsername()).orElse("anonymous");
        String argsSummary = summarize(pjp.getArgs());
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("OK   action={} user={} args={} took={}ms", action, username, argsSummary, ms);
            events.publishEvent(new AuditedEvent(
                action, userId, username, argsSummary, true, ms, null, null, traceId
            ));
            return result;
        } catch (Throwable t) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.warn("FAIL action={} user={} args={} took={}ms cause={}: {}",
                action, username, argsSummary, ms, t.getClass().getSimpleName(), t.getMessage());
            events.publishEvent(new AuditedEvent(
                action, userId, username, argsSummary, false, ms,
                t.getClass().getName(),
                truncate(t.getMessage(), 500),
                traceId
            ));
            throw t;
        }
    }

    private static String summarize(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        return truncate(Arrays.stream(args)
            .map(a -> {
                if (a == null) return "null";
                String s = a.toString();
                return s.length() > 80 ? s.substring(0, 77) + "..." : s;
            })
            .toList()
            .toString(), 1000);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
