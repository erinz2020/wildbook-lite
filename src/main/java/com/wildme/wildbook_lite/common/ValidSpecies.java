package com.wildme.wildbook_lite.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Custom validator. Allows letters, spaces, hyphens, apostrophes;
 * 2-64 chars; matches the kind of taxonomy names you'd see
 * ("Humpback whale", "Hawai'i 'akepa").
 *
 * Demonstrates how to plug in domain-specific constraints to
 * Jakarta Validation rather than scattering ad-hoc regex everywhere.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SpeciesValidator.class)
public @interface ValidSpecies {
    String message() default "species must be 2-64 chars (letters, spaces, '-, ')";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
