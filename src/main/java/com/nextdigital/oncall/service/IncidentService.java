package com.nextdigital.oncall.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nextdigital.oncall.dto.IncidentRequest;
import com.nextdigital.oncall.dto.IncidentResponse;

public interface IncidentService {

    /**
     * Lists incidents, optionally filtered by affected application, by owning
     * on-call group (each group only sees its own incidents) and by start date
     * range.
     */
    Page<IncidentResponse> findAll(Pageable pageable, UUID applicationId, UUID onCallGroupId,
            OffsetDateTime startedFrom, OffsetDateTime startedTo);

    IncidentResponse findById(UUID id);

    IncidentResponse create(IncidentRequest request);

    IncidentResponse update(UUID id, IncidentRequest request);

    void delete(UUID id);

    IncidentResponse linkCall(UUID incidentId, UUID callId);

    void unlinkCall(UUID incidentId, UUID callId);
}
