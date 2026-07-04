package com.nextdigital.oncall.service;

import java.util.List;
import java.util.UUID;

import com.nextdigital.oncall.dto.PersonRequest;
import com.nextdigital.oncall.dto.PersonResponse;
import com.nextdigital.oncall.entity.Person;

public interface PersonService {

    List<PersonResponse> findAll(boolean includeInactive);

    PersonResponse findById(UUID id);

    PersonResponse create(PersonRequest request);

    PersonResponse update(UUID id, PersonRequest request);

    void delete(UUID id);

    Person syncFromGoogle(String email, String name, String googleSub);
}
