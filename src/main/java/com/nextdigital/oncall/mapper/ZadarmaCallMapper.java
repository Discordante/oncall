package com.nextdigital.oncall.mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.nextdigital.oncall.dto.CallRecordResponse;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.ZadarmaCall;
import com.nextdigital.oncall.zadarma.dto.ZadarmaCallRecord;

public final class ZadarmaCallMapper {

    private static final DateTimeFormatter ZADARMA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                entity.getCreatedAt());
    }

    private static OffsetDateTime parseCallStart(String callstart) {
        if (callstart == null || callstart.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return LocalDateTime.parse(callstart, ZADARMA_FMT).atOffset(ZoneOffset.UTC);
    }
}
