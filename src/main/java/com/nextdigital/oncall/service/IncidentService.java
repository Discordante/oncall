package com.nextdigital.oncall.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nextdigital.oncall.dto.IncidentRequest;
import com.nextdigital.oncall.dto.IncidentResponse;

public interface IncidentService {

    Page<IncidentResponse> findAll(Pageable pageable, UUID applicationId);

    IncidentResponse findById(UUID id);

    IncidentResponse create(IncidentRequest request);

    IncidentResponse update(UUID id, IncidentRequest request);

    void delete(UUID id);

    IncidentResponse linkCall(UUID incidentId, UUID callId);

    void unlinkCall(UUID incidentId, UUID callId);
}
