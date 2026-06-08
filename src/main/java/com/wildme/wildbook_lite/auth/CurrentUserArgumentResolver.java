package com.wildme.wildbook_lite.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * Teaches Spring MVC how to fill a controller parameter marked @CurrentUser.
 *
 *  - supportsParameter:  return true if THIS resolver should handle it.
 *  - resolveArgument:    return the value to pass for that parameter.
 *
 * Why this matters in Spring Boot:
 *   This is the same extension point Spring itself uses to resolve
 *   @PathVariable, @RequestParam, @RequestBody, Pageable, etc.
 *   Recognising the pattern unlocks plug-in points across the framework.
 *
 *   Compare with a Filter (request-/response-stream level, runs for
 *   every URL) and a HandlerInterceptor (per-controller-method, but no
 *   parameter binding). ArgumentResolvers operate on individual
 *   parameters — most precise tool of the three.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && AppPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        return SecurityUtils.currentPrincipal()
            .orElseThrow(() -> new ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Authentication required"));
    }
}
