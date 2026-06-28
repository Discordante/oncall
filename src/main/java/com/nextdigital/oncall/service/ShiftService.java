package com.nextdigital.oncall.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.nextdigital.oncall.dto.ShiftRequest;
import com.nextdigital.oncall.dto.ShiftResponse;

public interface ShiftService {

    List<ShiftResponse> findAll(UUID personId, UUID onCallGroupId, OffsetDateTime from, OffsetDateTime to);

    ShiftResponse findById(UUID id);

    ShiftResponse create(ShiftRequest request);

    ShiftResponse update(UUID id, ShiftRequest request);

    void delete(UUID id);
}
