package com.wildme.wildbook_lite.common;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SpeciesValidator implements ConstraintValidator<ValidSpecies, String> {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z][A-Za-z '\\-]{1,63}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isBlank()) return false;
        return PATTERN.matcher(value).matches();
    }
}
