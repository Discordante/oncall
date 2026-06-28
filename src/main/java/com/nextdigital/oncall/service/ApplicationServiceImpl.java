package com.nextdigital.oncall.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.ApplicationRequest;
import com.nextdigital.oncall.dto.ApplicationResponse;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.mapper.ApplicationMapper;
import com.nextdigital.oncall.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse> findAll() {
        return repository.findAll().stream()
                .map(ApplicationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationResponse findById(UUID id) {
        return ApplicationMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public ApplicationResponse create(ApplicationRequest request) {
        var entity = ApplicationMapper.toEntity(request);
        var saved = repository.save(entity);
        log.info("Application created: {}", saved.getId());
        return ApplicationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ApplicationResponse update(UUID id, ApplicationRequest request) {
        var entity = findEntity(id);
        ApplicationMapper.updateEntity(request, entity);
        var saved = repository.save(entity);
        log.info("Application updated: {}", saved.getId());
        return ApplicationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var entity = findEntity(id);
        repository.delete(entity);
        log.info("Application deleted: {}", id);
    }

    private Application findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id));
    }
}
