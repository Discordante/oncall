package com.nextdigital.oncall.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, UUID id) {
        super("Resource '%s' with id '%s' not found".formatted(resource, id));
    }
}
