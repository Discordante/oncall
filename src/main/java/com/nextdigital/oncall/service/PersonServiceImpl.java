package com.nextdigital.oncall.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.PersonRequest;
import com.nextdigital.oncall.dto.PersonResponse;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.mapper.PersonMapper;
import com.nextdigital.oncall.repository.PersonRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonServiceImpl implements PersonService {

    private final PersonRepository repository;

    @Override
    @Transactional
    public Person syncFromGoogle(String email, String name, String googleSub) {
        var existing = repository.findByEmail(email);
        if (existing.isPresent()) {
            var person = existing.get();
            if (person.getGoogleSub() == null) {
                person.setGoogleSub(googleSub);
                person.setName(name);
                var saved = repository.save(person);
                log.info("Person updated with googleSub: {} ({})", saved.getId(), email);
                return saved;
            }
            log.debug("Person already has googleSub: {} ({})", person.getId(), email);
            return person;
        }

        var newPerson = Person.builder()
                .name(name)
                .email(email)
                .isActive(true)
                .googleSub(googleSub)
                .build();
        var saved = repository.save(newPerson);
        log.info("Person auto-created from Google SSO: {} ({})", saved.getId(), email);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonResponse> findAll(boolean includeInactive) {
        if (includeInactive) {
            return repository.findAll().stream()
                    .map(PersonMapper::toResponse)
                    .toList();
        }
        return repository.findByIsActiveTrue().stream()
                .map(PersonMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PersonResponse findById(UUID id) {
        return PersonMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public PersonResponse create(PersonRequest request) {
        var entity = PersonMapper.toEntity(request);
        var saved = repository.save(entity);
        log.info("Person created: {}", saved.getId());
        return PersonMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PersonResponse update(UUID id, PersonRequest request) {
        var entity = findEntity(id);
        PersonMapper.updateEntity(request, entity);
        var saved = repository.save(entity);
        log.info("Person updated: {}", saved.getId());
        return PersonMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var entity = findEntity(id);
        entity.setIsActive(false);
        repository.save(entity);
        log.info("Person soft-deleted: {}", id);
    }

    private Person findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person", id));
    }
}
