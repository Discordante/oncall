package com.nextdigital.oncall.validation;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StartBeforeEndValidator implements ConstraintValidator<StartBeforeEnd, Object> {

    private String startFieldName;
    private String endFieldName;

    @Override
    public void initialize(StartBeforeEnd annotation) {
        this.startFieldName = annotation.startField();
        this.endFieldName = annotation.endField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            var startField = getField(value.getClass(), startFieldName);
            var endField = getField(value.getClass(), endFieldName);

            startField.setAccessible(true);
            endField.setAccessible(true);

            var start = (OffsetDateTime) startField.get(value);
            var end = (OffsetDateTime) endField.get(value);

            if (start == null || end == null) {
                return true; // @NotNull se encarga de eso
            }

            return start.isBefore(end);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            return true;
        }
    }

    private Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // Para Java Records, los campos se almacenan como components privados
            if (clazz.getSuperclass() != null) {
                return clazz.getSuperclass().getDeclaredField(fieldName);
            }
            throw e;
        }
    }
}
