package com.nextdigital.oncall.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.IncidentRequest;
import com.nextdigital.oncall.dto.IncidentResponse;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.Incident;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.ZadarmaCall;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.mapper.IncidentMapper;
import com.nextdigital.oncall.repository.ApplicationRepository;
import com.nextdigital.oncall.repository.IncidentRepository;
import com.nextdigital.oncall.repository.IncidentSpecifications;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.ZadarmaCallRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;
    private final ApplicationRepository applicationRepository;
    private final OnCallGroupRepository groupRepository;
    private final ZadarmaCallRepository callRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> findAll(Pageable pageable, UUID applicationId, UUID onCallGroupId,
            OffsetDateTime startedFrom, OffsetDateTime startedTo) {
        var spec = IncidentSpecifications.withFilters(applicationId, onCallGroupId, startedFrom, startedTo);
        return incidentRepository.findAll(spec, pageable).map(IncidentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse findById(UUID id) {
        return IncidentMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public IncidentResponse create(IncidentRequest request) {
        var application = resolveApplication(request.applicationId());
        var calls = resolveCalls(request.callRecordIds());
        var onCallGroup = resolveGroup(request.onCallGroupId(), calls);
        var incident = IncidentMapper.toEntity(request, application, onCallGroup, calls);
        applyStartedAt(incident, request.startedAt());
        var saved = incidentRepository.save(incident);
        log.info("Incident created: {}", saved.getId());
        return IncidentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse update(UUID id, IncidentRequest request) {
        var incident = findEntity(id);
        var application = resolveApplication(request.applicationId());
        var calls = resolveCalls(request.callRecordIds());
        var onCallGroup = resolveGroup(request.onCallGroupId(), calls);
        IncidentMapper.updateEntity(request, incident, application, onCallGroup, calls);
        applyStartedAt(incident, request.startedAt());
        var saved = incidentRepository.save(incident);
        log.info("Incident updated: {}", saved.getId());
        return IncidentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var incident = findEntity(id);
        incidentRepository.delete(incident);
        log.info("Incident deleted: {}", id);
    }

    @Override
    @Transactional
    public IncidentResponse linkCall(UUID incidentId, UUID callId) {
        var incident = findEntity(incidentId);
        var call = callRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("ZadarmaCall", callId));
        incident.getCalls().add(call);
        // Adopt the group of the linked call when the incident has none
        if (incident.getOnCallGroup() == null && call.getOnCallGroup() != null) {
            incident.setOnCallGroup(call.getOnCallGroup());
        }
        recalculateStartedAt(incident);
        var saved = incidentRepository.save(incident);
        log.info("Call {} linked to incident {}", callId, incidentId);
        return IncidentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void unlinkCall(UUID incidentId, UUID callId) {
        var incident = findEntity(incidentId);
        var call = callRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("ZadarmaCall", callId));
        incident.getCalls().remove(call);
        recalculateStartedAt(incident);
        incidentRepository.save(incident);
        log.info("Call {} unlinked from incident {}", callId, incidentId);
    }

    // ─────────────────────────────────────────────
    // started_at management
    // ─────────────────────────────────────────────

    /**
     * On create/update: calls present → auto from earliest; no calls → use manual
     * value.
     */
    private void applyStartedAt(Incident incident, OffsetDateTime manualStartedAt) {
        if (incident.getCalls().isEmpty()) {
            incident.setStartedAt(manualStartedAt);
        } else {
            incident.setStartedAt(
                    incident.getCalls().stream()
                            .map(ZadarmaCall::getCallStartedAt)
                            .filter(Objects::nonNull)
                            .min(Comparator.naturalOrder())
                            .orElse(manualStartedAt));
        }
    }

    /** On link/unlink: recalculate from current calls; null if no calls remain. */
    private void recalculateStartedAt(Incident incident) {
        if (incident.getCalls().isEmpty()) {
            incident.setStartedAt(null);
        } else {
            incident.setStartedAt(
                    incident.getCalls().stream()
                            .map(ZadarmaCall::getCallStartedAt)
                            .filter(Objects::nonNull)
                            .min(Comparator.naturalOrder())
                            .orElse(null));
        }
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private Incident findEntity(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }

    /** Application is optional: incidents can be created without one. */
    private Application resolveApplication(UUID applicationId) {
        if (applicationId == null) {
            return null;
        }
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
    }

    /**
     * Owner group resolution: the explicit id wins; otherwise it is inferred
     * from the linked calls so incidents never end up without a group when they
     * come from a group context.
     */
    private OnCallGroup resolveGroup(UUID onCallGroupId, List<ZadarmaCall> calls) {
        if (onCallGroupId != null) {
            return groupRepository.findById(onCallGroupId)
                    .orElseThrow(() -> new ResourceNotFoundException("OnCallGroup", onCallGroupId));
        }
        if (calls == null || calls.isEmpty()) {
            return null;
        }
        return calls.stream()
                .map(ZadarmaCall::getOnCallGroup)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<ZadarmaCall> resolveCalls(List<UUID> callIds) {
        if (callIds == null) {
            return null; // null = preserve existing calls; empty list = clear all
        }
        if (callIds.isEmpty()) {
            return List.of();
        }
        return callRepository.findAllById(callIds);
    }
}
