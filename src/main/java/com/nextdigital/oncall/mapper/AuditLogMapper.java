package com.nextdigital.oncall.mapper;

import com.nextdigital.oncall.dto.AuditAction;
import com.nextdigital.oncall.dto.AuditLogResponse;
import com.nextdigital.oncall.entity.AuditLog;

import tools.jackson.databind.JsonNode;

public final class AuditLogMapper {

    private AuditLogMapper() {
    }

    public static AuditLogResponse toResponse(AuditLog log, JsonNode changes) {
        return new AuditLogResponse(
                log.getId(),
                log.getOccurredAt(),
                AuditAction.valueOf(log.getAction()),
                log.getEntityType(),
                log.getEntityId(),
                changes,
                log.getPersonId(),
                log.getActorEmail(),
                log.getActorName(),
                log.getIpAddress(),
                log.getHttpMethod(),
                log.getRequestPath(),
                log.getUserAgent());
    }
}
