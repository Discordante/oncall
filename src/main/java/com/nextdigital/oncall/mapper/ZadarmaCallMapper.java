package com.nextdigital.oncall.mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Objects;

import com.nextdigital.oncall.dto.CallRecordResponse;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.Incident;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.ZadarmaCall;
import com.nextdigital.oncall.zadarma.dto.ZadarmaCallRecord;

public final class ZadarmaCallMapper {

    private static final DateTimeFormatter ZADARMA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Zadarma returns call timestamps in the account timezone (Europe/Madrid). */
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Madrid");

    private ZadarmaCallMapper() {
    }

    public static ZadarmaCall toEntity(ZadarmaCallRecord dto, OnCallGroup group) {
        return ZadarmaCall.builder()
                .externalCallId(dto.id())
                .sipId(dto.sip())
                .onCallGroup(group)
                .callerNumber(dto.from() != 0 ? String.valueOf(dto.from()) : null)
                .destinationNumber(dto.to() != 0 ? String.valueOf(dto.to()) : null)
                .callStartedAt(parseCallStart(dto.callstart()))
                .billSeconds(dto.billseconds())
                .disposition(dto.disposition() != null ? dto.disposition().getValue() : "unknown")
                .description(dto.description())
                .build();
    }

    public static CallRecordResponse toResponse(ZadarmaCall entity) {
        var group = entity.getOnCallGroup();
        var incident = entity.getIncidents().stream()
                .min(Comparator.comparing(Incident::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        var application = incident != null ? incident.getApplication() : null;
        var applicationNames = entity.getIncidents().stream()
                .map(Incident::getApplication)
                .filter(Objects::nonNull)
                .map(Application::getName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return new CallRecordResponse(
                entity.getId(),
                entity.getExternalCallId(),
                entity.getSipId(),
                group != null ? group.getId() : null,
                group != null ? group.getName() : null,
                entity.getCallerNumber(),
                entity.getDestinationNumber(),
                entity.getCallStartedAt(),
                entity.getBillSeconds(),
                entity.getDisposition(),
                entity.getDescription(),
                incident != null ? incident.getId() : null,
                incident != null ? incident.getTitle() : null,
                application != null ? application.getId() : null,
                application != null ? application.getName() : null,
                applicationNames,
                entity.getCreatedAt());
    }

    private static OffsetDateTime parseCallStart(String callstart) {
        if (callstart == null || callstart.isBlank()) {
            return OffsetDateTime.now(APP_ZONE);
        }
        return LocalDateTime.parse(callstart, ZADARMA_FMT).atZone(APP_ZONE).toOffsetDateTime();
    }
}
