package com.nextdigital.oncall.mapper;

import com.nextdigital.oncall.dto.PersonRequest;
import com.nextdigital.oncall.dto.PersonResponse;
import com.nextdigital.oncall.entity.Person;

public final class PersonMapper {

    private PersonMapper() {
    }

    public static PersonResponse toResponse(Person entity) {
        return new PersonResponse(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getIsActive(),
                entity.getTelegramUserId() != null || entity.getTelegramChatId() != null,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static Person toEntity(PersonRequest request) {
        return Person.builder()
                .name(request.name())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
    }

    public static void updateEntity(PersonRequest request, Person entity) {
        entity.setName(request.name());
        entity.setEmail(request.email());
        entity.setPhoneNumber(request.phoneNumber());
        if (request.isActive() != null) {
            entity.setIsActive(request.isActive());
        }
    }
}
