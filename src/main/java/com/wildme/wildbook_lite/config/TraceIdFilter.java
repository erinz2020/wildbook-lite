package com.wildme.wildbook_lite.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-request correlation id.
 *
 *  - Reads X-Request-Id from the inbound request, or mints a fresh one.
 *  - Puts it into the SLF4J MDC so every log line in the request carries
 *    [traceId=xxx]. See logback pattern in application.yml.
 *  - Echos the same id back via X-Request-Id so clients can correlate.
 *
 * Interview gotcha:
 *  - MDC is ThreadLocal. When work crosses thread boundaries (@Async,
 *    parallel streams), MDC is empty by default. Use
 *    TaskDecorator to copy MDC into worker threads in production setups.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
