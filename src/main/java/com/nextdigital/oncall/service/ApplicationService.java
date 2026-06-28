package com.nextdigital.oncall.service;

import java.util.List;
import java.util.UUID;

import com.nextdigital.oncall.dto.ApplicationRequest;
import com.nextdigital.oncall.dto.ApplicationResponse;

public interface ApplicationService {

    List<ApplicationResponse> findAll();

    ApplicationResponse findById(UUID id);

    ApplicationResponse create(ApplicationRequest request);

    ApplicationResponse update(UUID id, ApplicationRequest request);

    void delete(UUID id);
}
