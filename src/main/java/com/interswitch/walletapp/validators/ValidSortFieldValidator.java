package com.interswitch.walletapp.validators;

import com.interswitch.walletapp.annotation.ValidSortField;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidSortFieldValidator implements ConstraintValidator<ValidSortField, String> {

    private Set<String> allowedFields;

    @Override
    public void initialize(ValidSortField annotation) {
        allowedFields = Arrays.stream(annotation.target().getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (allowedFields.contains(value)) return true;

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                String.format("'%s' is not a valid sort field.", value)
        ).addConstraintViolation();

        return false;
    }
}
