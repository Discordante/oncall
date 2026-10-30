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
import com.nextdigital.oncall.entity.Incident;
import com.nextdigital.oncall.entity.ZadarmaCall;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.mapper.IncidentMapper;
import com.nextdigital.oncall.repository.ApplicationRepository;
import com.nextdigital.oncall.repository.IncidentRepository;
import com.nextdigital.oncall.repository.ZadarmaCallRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;
    private final ApplicationRepository applicationRepository;
    private final ZadarmaCallRepository callRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> findAll(Pageable pageable, UUID applicationId) {
        var page = (applicationId != null)
                ? incidentRepository.findByApplicationId(applicationId, pageable)
                : incidentRepository.findAll(pageable);
        return page.map(IncidentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse findById(UUID id) {
        return IncidentMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public IncidentResponse create(IncidentRequest request) {
        var application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application", request.applicationId()));
        var calls = resolveCalls(request.callRecordIds());
        var incident = IncidentMapper.toEntity(request, application, calls);
        applyStartedAt(incident, request.startedAt());
        var saved = incidentRepository.save(incident);
        log.info("Incident created: {}", saved.getId());
        return IncidentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse update(UUID id, IncidentRequest request) {
        var incident = findEntity(id);
        var application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application", request.applicationId()));
        var calls = resolveCalls(request.callRecordIds());
        IncidentMapper.updateEntity(request, incident, application, calls);
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

    private List<ZadarmaCall> resolveCalls(List<UUID> callIds) {
        if (callIds == null || callIds.isEmpty()) {
            return List.of();
        }
        return callRepository.findAllById(callIds);
    }
}
