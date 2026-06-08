package com.wildme.wildbook_lite.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inject the current authenticated user directly into a controller
 * method:
 *
 *   @GetMapping("/me/projects")
 *   public List<Project> myProjects(@CurrentUser AppPrincipal me) { ... }
 *
 * Hooked up by CurrentUserArgumentResolver, registered in WebMvcConfig.
 *
 * Spring Security has @AuthenticationPrincipal which does the same thing
 * — this is here mainly to demonstrate the
 * HandlerMethodArgumentResolver extension point, which is the standard
 * way you teach Spring MVC to understand custom controller parameters.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
