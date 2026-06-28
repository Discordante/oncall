package com.nextdigital.oncall.mapper;

import com.nextdigital.oncall.dto.ShiftRequest;
import com.nextdigital.oncall.dto.ShiftResponse;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.Shift;

public final class ShiftMapper {

    private ShiftMapper() {
    }

    public static ShiftResponse toResponse(Shift entity) {
        return new ShiftResponse(
                entity.getId(),
                entity.getOnCallGroup().getId(),
                entity.getOnCallGroup().getName(),
                entity.getPerson().getId(),
                entity.getPerson().getName(),
                entity.getPerson().getEmail(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static Shift toEntity(ShiftRequest request, OnCallGroup group, Person person) {
        return Shift.builder()
                .onCallGroup(group)
                .person(person)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();
    }

    public static void updateEntity(ShiftRequest request, Shift entity,
            OnCallGroup group, Person person) {
        entity.setOnCallGroup(group);
        entity.setPerson(person);
        entity.setStartTime(request.startTime());
        entity.setEndTime(request.endTime());
    }
}
