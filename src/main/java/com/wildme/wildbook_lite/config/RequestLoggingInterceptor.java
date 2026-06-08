package com.wildme.wildbook_lite.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Log every successful request with timing. Runs as a Spring MVC
 * HandlerInterceptor, NOT a Servlet Filter — and the difference matters
 * (interview food):
 *
 *  Filter (TraceIdFilter is one):
 *    - Servlet-level. Runs before DispatcherServlet even sees the
 *      request. Can wrap/replace the response. Great for cross-cutting
 *      concerns that aren't tied to a specific handler — trace ids, CORS,
 *      auth, rate limits.
 *
 *  HandlerInterceptor (this one):
 *    - Spring MVC-level. Runs *inside* DispatcherServlet, AFTER it has
 *      resolved which controller method (HandlerMethod) will run. So it
 *      knows things like which @Controller method, which @RequestMapping
 *      pattern. Good for logging "method X took N ms" or skipping the
 *      response body for selected handlers.
 *
 *  preHandle  — before controller method
 *  postHandle — after controller method, before view rendering
 *  afterCompletion — after the whole request, even on exception
 *
 *  Store per-request state with a request attribute, NOT a field on the
 *  bean — the bean is a singleton serving concurrent requests.
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("http");
    private static final String START_ATTR = "_http_start_ns";

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        req.setAttribute(START_ATTR, System.nanoTime());
        return true; // false would short-circuit the request
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp,
                                Object handler, Exception ex) {
        Long start = (Long) req.getAttribute(START_ATTR);
        long ms = start == null ? -1 : (System.nanoTime() - start) / 1_000_000;
        String tag = ex == null ? "OK" : "ERR";
        log.info("{} {} {} -> {} took={}ms{}",
            tag,
            req.getMethod(),
            req.getRequestURI(),
            resp.getStatus(),
            ms,
            ex == null ? "" : " ex=" + ex.getClass().getSimpleName());
    }
}
