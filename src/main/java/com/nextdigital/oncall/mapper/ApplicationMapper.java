package com.nextdigital.oncall.mapper;

import com.nextdigital.oncall.dto.ApplicationRequest;
import com.nextdigital.oncall.dto.ApplicationResponse;
import com.nextdigital.oncall.entity.Application;

public final class ApplicationMapper {

    private ApplicationMapper() {
    }

    public static ApplicationResponse toResponse(Application entity) {
        return new ApplicationResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static Application toEntity(ApplicationRequest request) {
        return Application.builder()
                .name(request.name())
                .description(request.description())
                .build();
    }

    public static void updateEntity(ApplicationRequest request, Application entity) {
        entity.setName(request.name());
        entity.setDescription(request.description());
    }
}
