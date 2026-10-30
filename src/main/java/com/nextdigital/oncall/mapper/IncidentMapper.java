package com.nextdigital.oncall.mapper;

import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;

import com.nextdigital.oncall.dto.IncidentRequest;
import com.nextdigital.oncall.dto.IncidentResponse;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.Incident;
import com.nextdigital.oncall.entity.ZadarmaCall;

public final class IncidentMapper {

    private IncidentMapper() {
    }

    public static Incident toEntity(IncidentRequest request, Application application, List<ZadarmaCall> calls) {
        return Incident.builder()
                .title(request.title())
                .description(request.description())
                .application(application)
                .bridgeOpened(request.bridgeOpened())
                .endedAt(request.endedAt())
                .calls(calls != null ? new HashSet<>(calls) : new HashSet<>())
                .build();
        // startedAt not set here — managed by service after this call
    }

    public static IncidentResponse toResponse(Incident entity) {
        var app = entity.getApplication();
        return new IncidentResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                app != null ? app.getId() : null,
                app != null ? app.getName() : null,
                entity.isBridgeOpened(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                computeDurationSeconds(entity),
                entity.getCalls().size(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static void updateEntity(IncidentRequest request, Incident entity, Application application,
            List<ZadarmaCall> calls) {
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setApplication(application);
        entity.setBridgeOpened(request.bridgeOpened());
        entity.setEndedAt(request.endedAt());
        if (calls != null) { // null = callRecordIds was absent → preserve existing
            entity.getCalls().clear();
            entity.getCalls().addAll(calls);
        }
        // startedAt managed by service after this call
    }

    private static Long computeDurationSeconds(Incident entity) {
        if (entity.getStartedAt() == null || entity.getEndedAt() == null) {
            return null;
        }
        return ChronoUnit.SECONDS.between(entity.getStartedAt(), entity.getEndedAt());
    }
}
