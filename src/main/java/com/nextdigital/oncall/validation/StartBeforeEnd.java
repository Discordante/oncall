package com.nextdigital.oncall.validation;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = StartBeforeEndValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface StartBeforeEnd {

    String message() default "La fecha de inicio debe ser anterior a la fecha de fin";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String startField() default "startTime";

    String endField() default "endTime";
}
