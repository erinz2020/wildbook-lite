package com.wildme.wildbook_lite.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that should be audited (logged with user, args, latency, outcome).
 * Pick up by AuditAspect at runtime.
 *
 * Usage:
 *   @Audited("project.create")
 *   public Project create(...) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /** Human-readable action name. Falls back to ClassName.methodName when blank. */
    String value() default "";
}
