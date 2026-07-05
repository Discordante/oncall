package com.nextdigital.oncall.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.dto.OnCallGroupResponse;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.mapper.OnCallGroupMapper;
import com.nextdigital.oncall.repository.ApplicationRepository;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.telegram.service.TelegramBotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnCallGroupServiceImpl implements OnCallGroupService {

    private final OnCallGroupRepository repository;
    private final ApplicationRepository applicationRepository;
    private final PersonRepository personRepository;
    private final TelegramBotService telegramBotService;

    @Override
    @Transactional(readOnly = true)
    public List<OnCallGroupResponse> findAll() {
        return repository.findAll().stream()
                .map(OnCallGroupMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OnCallGroupResponse findById(UUID id) {
        return OnCallGroupMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public OnCallGroupResponse create(OnCallGroupRequest request) {
        var applications = resolveApplications(request.applicationIds());
        var people = resolvePeople(request.personIds());
        var entity = OnCallGroupMapper.toEntity(request, applications, people);
        var saved = repository.save(entity);
        log.info("OnCallGroup created: {}", saved.getId());

        var config = saved.getTelegramGroupConfig();
        if (config != null && Boolean.TRUE.equals(config.getBotEnabled())) {
            telegramBotService.registerGroupBot(saved.getId());
            telegramBotService.generateGroupInviteLink(saved.getId());
        }

        return OnCallGroupMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public OnCallGroupResponse update(UUID id, OnCallGroupRequest request) {
        var entity = findEntity(id);
        var oldConfig = entity.getTelegramGroupConfig();
        boolean wasEnabled = oldConfig != null && Boolean.TRUE.equals(oldConfig.getBotEnabled());
        var applications = resolveApplications(request.applicationIds());
        var people = resolvePeople(request.personIds());
        OnCallGroupMapper.updateEntity(request, entity, applications, people);
        var saved = repository.save(entity);
        log.info("OnCallGroup updated: {}", saved.getId());

        var newConfig = saved.getTelegramGroupConfig();
        boolean nowEnabled = newConfig != null && Boolean.TRUE.equals(newConfig.getBotEnabled());
        if (nowEnabled && !wasEnabled) {
            telegramBotService.registerGroupBot(saved.getId());
            telegramBotService.generateGroupInviteLink(saved.getId());
        } else if (!nowEnabled && wasEnabled) {
            telegramBotService.unregisterGroupBot(saved.getId());
        }

        return OnCallGroupMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var entity = findEntity(id);
        telegramBotService.unregisterGroupBot(id);
        repository.delete(entity);
        log.info("OnCallGroup deleted: {}", id);
    }

    private OnCallGroup findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OnCallGroup", id));
    }

    private Set<Application> resolveApplications(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream()
                .map(appId -> applicationRepository.findById(appId)
                        .orElseThrow(() -> new ResourceNotFoundException("Application", appId)))
                .collect(Collectors.toSet());
    }

    private Set<Person> resolvePeople(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream()
                .map(personId -> personRepository.findById(personId)
                        .orElseThrow(() -> new ResourceNotFoundException("Person", personId)))
                .collect(Collectors.toSet());
    }
}
