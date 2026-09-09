package com.nextdigital.oncall.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.AuditLogFilter;
import com.nextdigital.oncall.dto.AuditLogResponse;
import com.nextdigital.oncall.entity.AuditLog;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.mapper.AuditLogMapper;
import com.nextdigital.oncall.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable) {
        return repository.findAll(buildSpec(filter), pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse findById(UUID id) {
        var auditLog = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", id));
        return toResponse(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> entityTypes() {
        return repository.findDistinctEntityTypes();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogMapper.toResponse(log, parseChanges(log.getChanges()));
    }

    private JsonNode parseChanges(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("No se pudo parsear el JSON de cambios del registro de auditoría: {}", e.getMessage());
            return null;
        }
    }

    private Specification<AuditLog> buildSpec(AuditLogFilter f) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();

        if (f.action() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), f.action().name()));
        }
        if (hasText(f.entityType())) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), f.entityType()));
        }
        if (hasText(f.entityId())) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), f.entityId()));
        }
        if (f.personId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("personId"), f.personId()));
        }
        if (f.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from()));
        }
        if (f.to() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), f.to()));
        }
        if (hasText(f.q())) {
            String pattern = "%" + f.q().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("actorEmail")), pattern),
                    cb.like(cb.lower(root.get("actorName")), pattern),
                    cb.like(cb.lower(root.get("entityType")), pattern),
                    cb.like(cb.lower(root.get("entityId")), pattern),
                    cb.like(cb.lower(root.get("requestPath")), pattern)));
        }
        return spec;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
