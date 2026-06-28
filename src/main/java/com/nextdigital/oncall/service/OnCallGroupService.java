package com.nextdigital.oncall.service;

import java.util.List;
import java.util.UUID;

import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.dto.OnCallGroupResponse;

public interface OnCallGroupService {

    List<OnCallGroupResponse> findAll();

    OnCallGroupResponse findById(UUID id);

    OnCallGroupResponse create(OnCallGroupRequest request);

    OnCallGroupResponse update(UUID id, OnCallGroupRequest request);

    void delete(UUID id);
}
