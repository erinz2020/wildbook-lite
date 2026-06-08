package com.wildme.wildbook_lite.config;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Minimal in-process per-IP fixed-window rate limiter, applied only to
 * /api/auth/login and /api/auth/register.
 *
 * Real production:
 *  - this is JVM-local; for a multi-node deploy, lift to Redis (Bucket4j +
 *    Redis backend, or Spring Cloud Gateway's RequestRateLimiter).
 *  - Fixed-window has the classic edge: a burst right around the boundary
 *    effectively doubles the budget. Use sliding-window or token-bucket
 *    when accuracy matters.
 *
 * Why this lives at the auth boundary:
 *  - The point is to slow credential stuffing / brute force; per-IP is
 *    fine here. Account lockout would be more targeted but invites a DoS
 *    vector ("lock out my victim's account").
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT_PER_MINUTE = 10;
    private static final long WINDOW_MS = 60_000L;

    private record Window(long startMs, AtomicInteger count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/auth/login") && !path.startsWith("/api/auth/register")) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        long now = System.currentTimeMillis();

        Window window = windows.compute(ip, (k, existing) -> {
            if (existing == null || now - existing.startMs() > WINDOW_MS) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });

        int hits = window.count().incrementAndGet();
        if (hits > LIMIT_PER_MINUTE) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded, try again later\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // first IP in the comma-separated list is the original client
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
