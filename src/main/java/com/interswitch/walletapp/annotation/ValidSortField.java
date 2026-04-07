package com.interswitch.walletapp.annotation;

import com.interswitch.walletapp.validators.ValidSortFieldValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidSortFieldValidator.class)
public @interface ValidSortField {
    Class<?> target();

    String message() default "Invalid sort field";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
