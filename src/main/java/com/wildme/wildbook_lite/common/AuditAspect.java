package com.wildme.wildbook_lite.common;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.wildme.wildbook_lite.auth.SecurityUtils;

/**
 * Cross-cutting audit logger for methods annotated with @Audited.
 *
 * Interview points:
 *
 *  - Spring AOP uses JDK dynamic proxy for interfaces, CGLIB for classes.
 *    This is *proxy-based*: same-class internal calls do NOT trigger the
 *    aspect (the call bypasses the proxy). Classic gotcha; also why
 *    @Transactional on a private helper called from a public method fails
 *    silently.
 *
 *  - Order of advice on a join point: @Around outermost, then @Before/@After.
 *    Multiple aspects ordered via @Order.
 *
 *  - We deliberately catch the throwable, log, then rethrow — never swallow.
 *
 *  - We grab current user from SecurityContext, falling back to "anonymous"
 *    when no auth (e.g. /api/auth/login itself).
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger("audit");

    @Around("@annotation(audited) || @within(audited)")
    public Object aroundAudited(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        String action = audited.value();
        if (action == null || action.isBlank()) {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            action = sig.getDeclaringType().getSimpleName() + "." + sig.getName();
        }

        String user = SecurityUtils.currentPrincipal()
            .map(p -> p.getUsername() + "(" + p.getUserId() + ")")
            .orElse("anonymous");

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("OK   action={} user={} args={} took={}ms",
                action, user, summarize(pjp.getArgs()), ms);
            return result;
        } catch (Throwable t) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.warn("FAIL action={} user={} args={} took={}ms cause={}: {}",
                action, user, summarize(pjp.getArgs()), ms,
                t.getClass().getSimpleName(), t.getMessage());
            throw t;
        }
    }

    /** Best-effort short summary; trim long strings, avoid huge arg dumps. */
    private static String summarize(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        return Arrays.stream(args)
            .map(a -> {
                if (a == null) return "null";
                String s = a.toString();
                return s.length() > 80 ? s.substring(0, 77) + "..." : s;
            })
            .toList()
            .toString();
    }
}
